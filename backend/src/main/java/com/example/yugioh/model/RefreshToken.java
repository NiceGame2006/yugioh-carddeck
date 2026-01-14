package com.example.yugioh.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Refresh Token entity for storing long-lived tokens in database.
 * 
 * Purpose:
 * - Access tokens (JWT) expire quickly (15 min) for security
 * - Refresh tokens last longer (7 days) and allow getting new access tokens
 * - Stored in database so they can be revoked (unlike stateless JWTs)
 * 
 * Flow:
 * 1. User logs in → receives access token (JWT) + refresh token (UUID)
 * 2. Access token expires after 15 min
 * 3. Frontend calls /api/auth/refresh with refresh token
 * 4. Backend checks database: is refresh token valid and not expired?
 * 5. If valid → issue new access token (user stays logged in)
 * 6. If invalid/expired → user must login again with password
 * 
 * Security:
 * - Can revoke specific refresh tokens (logout from one device)
 * - Can revoke all user's refresh tokens (logout from all devices)
 * - Can track: which device, IP, last used time
 * - Refresh tokens are opaque (random UUID, not JWT)
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String token; // Random UUID
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Instant expiryDate;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    private Instant lastUsedAt;
    
    @Column(nullable = false)
    private boolean revoked = false;
    
    public RefreshToken() {
        this.createdAt = Instant.now();
    }
    
    public RefreshToken(String token, User user, Instant expiryDate) {
        this.token = token;
        this.user = user;
        this.expiryDate = expiryDate;
        this.createdAt = Instant.now();
        this.revoked = false;
    }
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Instant getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Instant expiryDate) { this.expiryDate = expiryDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public boolean isExpired() { return Instant.now().isAfter(this.expiryDate); }
}
