package com.example.yugioh.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that runs on every request.
 * 
 * Flow:
 * 1. Extract JWT token from Authorization header (Bearer <token>)
 * 2. Validate token signature and expiration
 * 3. Extract username and roles from token (no database lookup!)
 * 4. Create Authentication object and set in SecurityContext
 * 5. Request proceeds to controller with authentication
 * 
 * Replaces BasicAuthenticationFilter from HTTP Basic Auth approach.
 * 
 * Key difference from HTTP Basic Auth:
 * - Basic Auth: Decodes credentials → queries database → verifies password (slow)
 * - JWT: Validates signature → extracts data from token (fast, no database)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    
    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // 1. Extract JWT token from Authorization header
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Remove "Bearer " prefix
            
            // 2. Validate token
            if (jwtUtil.validateToken(token)) {
                // 3. Extract user data from token (self-contained - no database!)
                String username = jwtUtil.getUsernameFromToken(token);
                List<GrantedAuthority> authorities = jwtUtil.getRolesFromToken(token);
                
                // 4. Create Authentication object
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // 5. Set in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        
        // Continue filter chain
        filterChain.doFilter(request, response);
    }
}
