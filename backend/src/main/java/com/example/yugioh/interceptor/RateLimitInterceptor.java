package com.example.yugioh.interceptor;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate limiting interceptor using Redis-backed Bucket4j token bucket algorithm.
 * 
 * Rate Limits (per user/IP):
 * - Login endpoint: 5 attempts per minute (prevent brute force)
 * - Card search: 20 requests per minute (expensive database queries)
 * - Admin card operations: 30 requests per minute (write operations)
 * - General API: 100 requests per minute (default limit)
 * 
 * Token Bucket Algorithm:
 * - Each user gets a "bucket" with a fixed capacity of tokens
 * - Each request consumes 1 token
 * - Tokens refill at a fixed rate (e.g., 5 tokens per minute)
 * - If bucket is empty, request is rejected with 429 Too Many Requests
 * 
 * Redis Storage:
 * - Buckets stored with key: rate_limit:{username|ip}:{endpoint}
 * - Distributed: works across multiple backend instances
 * - Persistent: survives application restarts
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final ProxyManager<String> proxyManager;
    
    public RateLimitInterceptor(ProxyManager<String> proxyManager) {
        this.proxyManager = proxyManager;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Determine rate limit based on endpoint
        Supplier<BucketConfiguration> configSupplier;
        
        if (path.startsWith("/api/auth/login") && "POST".equals(method)) {
            // Login: 5 attempts per minute (strict to prevent brute force)
            configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
                .build();
        } else if (path.startsWith("/api/cards") && "GET".equals(method) && request.getParameter("query") != null) {
            // Card search: 20 requests per minute (expensive queries)
            configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(20).refillGreedy(20, Duration.ofMinutes(1)).build())
                .build();
        } else if (path.startsWith("/api/cards") && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            // Admin card operations: 30 requests per minute
            configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
                .build();
        } else if (path.startsWith("/actuator/")) {
            // No rate limit for health checks
            return true;
        } else {
            // General API: 100 requests per minute
            configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build())
                .build();
        }
        
        // Create unique key per user and endpoint
        String key = getRateLimitKey(request, path);
        
        // Get or create bucket from Redis
        Bucket bucket = proxyManager.builder().build(key, configSupplier);
        
        // Try to consume 1 token
        if (bucket.tryConsume(1)) {
            return true; // Allow request
        } else {
            // Rate limit exceeded
            sendRateLimitError(response);
            return false; // Block request
        }
    }
    
    /**
     * Creates a unique rate limit key based on authenticated user or IP address.
     * Format: rate_limit:{username|ip}:{endpoint}
     */
    private String getRateLimitKey(HttpServletRequest request, String path) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        String identifier;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            // Use username for authenticated users
            identifier = auth.getName();
        } else {
            // Use IP address for anonymous users
            identifier = getClientIP(request);
        }
        
        // Normalize path to group similar endpoints
        String normalizedPath = normalizePath(path);
        
        return "rate_limit:" + identifier + ":" + normalizedPath;
    }
    
    /**
     * Normalizes path to group similar endpoints together.
     * Example: /api/cards/123 -> /api/cards/*
     */
    private String normalizePath(String path) {
        if (path.matches("/api/cards/[^/]+")) {
            return "/api/cards/*";
        } else if (path.matches("/api/decks/[^/]+")) {
            return "/api/decks/*";
        } else if (path.matches("/api/archetypes/[^/]+")) {
            return "/api/archetypes/*";
        }
        return path;
    }
    
    /**
     * Gets client IP address, checking X-Forwarded-For header first (for proxies).
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Sends 429 Too Many Requests response with JSON error message.
     */
    private void sendRateLimitError(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded. Please try again later.\"}");
    }
}
