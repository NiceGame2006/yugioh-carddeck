package com.example.yugioh.controller;

import com.example.yugioh.dto.ResponseEnvelope;
import com.example.yugioh.model.RefreshToken;
import com.example.yugioh.model.User;
import com.example.yugioh.repository.UserRepository;
import com.example.yugioh.security.JwtUtil;
import com.example.yugioh.service.CustomUserDetailsService;
import com.example.yugioh.service.RefreshTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    
    public AuthController(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService, 
                         PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                         UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    /**
     * Get current user info (username and roles)
     * Works with JWT - extracts info from Authentication object set by JwtAuthenticationFilter
     */
    @GetMapping("/user")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            Map<String, Object> guestInfo = new HashMap<>();
            guestInfo.put("username", "guest");
            guestInfo.put("roles", new String[]{"GUEST"});
            guestInfo.put("authenticated", false);
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("Guest user", guestInfo);
            return ResponseEntity.ok(env);
        }
        
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", authentication.getName());
        userInfo.put("roles", authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(role -> role.replace("ROLE_", "")) // Remove ROLE_ prefix
            .collect(Collectors.toList()));
        userInfo.put("authenticated", true);
        
        ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("User authenticated", userInfo);
        return ResponseEntity.ok(env);
    }
    
    /**
     * JWT Login endpoint - accepts credentials and returns JWT access token + refresh token.
     * 
     * Frontend sends: { "username": "user1", "password": "password1" }
     * Backend returns: { 
     *   "accessToken": "eyJhbGci...",  // JWT, expires in 15 min
     *   "refreshToken": "uuid...",      // Random UUID, expires in 7 days
     *   "username": "user1", 
     *   "roles": ["USER"] 
     * }
     * 
     * Flow:
     * 1. Receive username/password in request body
     * 2. Load user from database via CustomUserDetailsService
     * 3. Verify password with BCrypt
     * 4. Generate JWT access token (short-lived, 15 min)
     * 5. Generate refresh token (long-lived, 7 days, stored in DB)
     * 6. Return both tokens to client
     */
    @PostMapping("/login")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        if (username == null || password == null) {
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.failed("Username and password required");
            return ResponseEntity.status(400).body(env);
        }
        
        try {
            // Load user from database
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
            
            // Verify password
            if (!passwordEncoder.matches(password, userDetails.getPassword())) {
                throw new BadCredentialsException("Invalid credentials");
            }
            
            // Extract roles for token generation
            List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            
            // Generate JWT access token (short-lived)
            String accessToken = jwtUtil.generateToken(username, roles);
            
            // Generate refresh token (long-lived, stored in DB)
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
            
            // Prepare response with both tokens and user info
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("accessToken", accessToken);
            responseData.put("refreshToken", refreshToken.getToken());
            responseData.put("username", userDetails.getUsername());
            responseData.put("roles", roles.stream()
                .map(role -> role.replace("ROLE_", ""))
                .collect(Collectors.toList()));
            responseData.put("authenticated", true);
            
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("Login successful", responseData);
            return ResponseEntity.ok(env);
            
        } catch (Exception e) {
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.failed("Invalid credentials");
            return ResponseEntity.status(401).body(env);
        }
    }
    
    /**
     * Refresh endpoint - exchanges valid refresh token for new access token.
     * 
     * Frontend sends: { "refreshToken": "uuid..." }
     * Backend returns: { "accessToken": "new_jwt...", "refreshToken": "same_or_new_uuid..." }
     * 
     * Flow:
     * 1. Receive refresh token from client
     * 2. Validate refresh token (exists, not expired, not revoked)
     * 3. Get user from refresh token
     * 4. Generate new JWT access token
     * 5. Return new access token (optionally rotate refresh token)
     * 
     * Called automatically by frontend when access token expires (401).
     */
    @PostMapping("/refresh")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> refresh(@RequestBody Map<String, String> request) {
        String refreshTokenStr = request.get("refreshToken");
        
        if (refreshTokenStr == null) {
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.failed("Refresh token required");
            return ResponseEntity.status(400).body(env);
        }
        
        try {
            // Validate refresh token
            RefreshToken refreshToken = refreshTokenService.validateRefreshToken(refreshTokenStr)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
            
            User user = refreshToken.getUser();
            
            // Extract roles
            List<String> roles = user.getRoles().stream()
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toList());
            
            // Generate new access token
            String newAccessToken = jwtUtil.generateToken(user.getUsername(), roles);
            
            // Prepare response
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("accessToken", newAccessToken);
            responseData.put("refreshToken", refreshToken.getToken()); // Same refresh token
            
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("Token refreshed", responseData);
            return ResponseEntity.ok(env);
            
        } catch (Exception e) {
            ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.failed("Invalid refresh token - please login again");
            return ResponseEntity.status(401).body(env);
        }
    }
    
    /**
     * Logout endpoint - revokes refresh token.
     * Since JWT access tokens are stateless, we can't revoke them directly.
     * But we can revoke the refresh token so no new access tokens can be obtained.
     */
    @PostMapping("/logout")
    public ResponseEntity<ResponseEnvelope<Void>> logout(@RequestBody Map<String, String> request) {
        String refreshTokenStr = request.get("refreshToken");
        
        if (refreshTokenStr != null) {
            refreshTokenService.revokeRefreshToken(refreshTokenStr);
        }
        
        ResponseEnvelope<Void> env = ResponseEnvelope.success("Logged out successfully");
        return ResponseEntity.ok(env);
    }
    
    /* ========== OLD HTTP BASIC AUTH LOGIN (COMMENTED OUT FOR REFERENCE) ==========
     * 
     * This was the previous login endpoint that relied on Spring Security's HTTP Basic Auth.
     * It didn't handle credentials directly - Spring Security did that automatically.
     * 
     * @PostMapping("/login")
     * public ResponseEntity<ResponseEnvelope<Map<String, Object>>> login(Authentication authentication) {
     *     if (authentication == null || !authentication.isAuthenticated()) {
     *         ResponseEnvelope<Map<String, Object>> env = new ResponseEnvelope<>(false, "Authentication required");
     *         return ResponseEntity.status(401).body(env);
     *     }
     *     
     *     Map<String, Object> userInfo = new HashMap<>();
     *     userInfo.put("username", authentication.getName());
     *     userInfo.put("roles", authentication.getAuthorities().stream()
     *         .map(GrantedAuthority::getAuthority)
     *         .map(role -> role.replace("ROLE_", ""))
     *         .collect(Collectors.toList()));
     *     userInfo.put("authenticated", true);
     *     
     *     ResponseEnvelope<Map<String, Object>> env = new ResponseEnvelope<>(true, "Login successful", userInfo);
     *     return ResponseEntity.ok(env);
     * }
     * 
     * ========== END OF OLD HTTP BASIC AUTH LOGIN ==========
     */
}
