package com.example.yugioh.config;

import com.example.yugioh.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Yu-Gi-Oh card management app.
 * 
 * NOW USING: JWT (JSON Web Token) Authentication
 * PREVIOUSLY: HTTP Basic Authentication (commented out at bottom of file)
 * 
 * Roles and Permissions:
 * - GUEST (unauthenticated): Can view cards, archetypes, and decks (read-only)
 * - USER: Can create/edit/delete their own decks, view cards (cannot modify cards)
 * - ADMIN: Full access - can create/delete cards and manage all decks
 * 
 * JWT Authentication Flow:
 * 1. User sends credentials to /api/auth/login
 * 2. AuthController verifies password via CustomUserDetailsService
 * 3. If valid, AuthController generates JWT token using JwtUtil
 * 4. Client stores JWT token (sessionStorage)
 * 5. Subsequent requests: Client sends JWT in Authorization: Bearer <token>
 * 6. JwtAuthenticationFilter intercepts, validates token, sets SecurityContext
 * 7. Request proceeds to controller with Authentication available
 * 
 * Why JWT instead of HTTP Basic:
 * - No database lookup on every request (token is self-contained)
 * - Faster (just signature verification)
 * - More scalable (stateless, no session storage)
 * - Modern standard for REST APIs
 * 
 * Security Approach:
 * Centralized security rules via SecurityFilterChain rather than method-level @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }
    
    /**
     * Configures HTTP security rules and authentication mechanism.
     * Spring Security automatically uses any UserDetailsService bean (CustomUserDetailsService)
     * found in the application context for authentication.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - anyone can view (GET requests)
                .requestMatchers(HttpMethod.GET, "/api/cards/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/archetypes/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/decks/**").permitAll()
                
                // Card management - ADMIN only
                .requestMatchers(HttpMethod.POST, "/api/cards/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/cards/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/cards/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/cards/**").hasRole("ADMIN")
                
                // Deck management - USER or ADMIN
                .requestMatchers(HttpMethod.POST, "/api/decks/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/decks/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/decks/**").hasAnyRole("USER", "ADMIN")
                
                // Archetype management - ADMIN only
                .requestMatchers(HttpMethod.POST, "/api/archetypes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/archetypes/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/archetypes/**").hasRole("ADMIN")
                
                // Actuator and health endpoints
                .requestMatchers("/actuator/**").permitAll()
                
                // Auth endpoints
                .requestMatchers("/api/auth/**").permitAll()
                
                // Users endpoint - ADMIN only
                .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            // JWT Authentication Configuration
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // No sessions, JWT is stateless
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class) // Add JWT filter
            .csrf(csrf -> csrf.disable()); // Disable CSRF for JWT (stateless, no cookies)
            
        /* ========== OLD HTTP BASIC AUTH CONFIGURATION (COMMENTED OUT FOR REFERENCE) ==========
         * 
         * This was the previous authentication approach. Kept for easy rollback if needed.
         * 
         * .httpBasic(basic -> {}) // Enable HTTP Basic Auth for API
         * .formLogin(form -> form.disable()) // Disable form login (using Basic Auth)
         * .logout(logout -> logout
         *     .logoutUrl("/api/auth/logout")
         *     .logoutSuccessHandler((request, response, authentication) -> {
         *         response.setStatus(200);
         *         response.getWriter().write("{\"success\":true,\"message\":\"Logged out successfully\"}");
         *     })
         * )
         * 
         * ========== END OF OLD HTTP BASIC AUTH ==========
         */
            
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
