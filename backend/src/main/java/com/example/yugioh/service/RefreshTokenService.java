package com.example.yugioh.service;

import com.example.yugioh.model.RefreshToken;
import com.example.yugioh.model.User;
import com.example.yugioh.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens.
 * 
 * Refresh tokens enable long-lived sessions without compromising security:
 * - Access tokens (JWT) expire quickly (15 min) and are stateless
 * - Refresh tokens last longer (7 days) and are stored in database
 * - Can be revoked if compromised or user logs out
 * 
 * Why store in database?
 * - Stateless JWTs can't be revoked until they expire
 * - Database storage allows instant revocation
 * - Can track usage patterns and suspicious activity
 */
@Service
public class RefreshTokenService {
    
    private final RefreshTokenRepository refreshTokenRepository;
    
    @Value("${jwt.refresh-expiration:604800000}") // 7 days in milliseconds
    private long refreshExpiration;
    
    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }
    
    /**
     * Creates a new refresh token for user.
     * Returns random UUID stored in database with expiration.
     */
    public RefreshToken createRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(refreshExpiration);
        
        RefreshToken refreshToken = new RefreshToken(token, user, expiryDate);
        return refreshTokenRepository.save(refreshToken);
    }
    
    /**
     * Validates refresh token: exists, not expired, not revoked.
     */
    public Optional<RefreshToken> validateRefreshToken(String token) {
        return refreshTokenRepository.findByToken(token)
            .filter(rt -> !rt.isExpired())
            .filter(rt -> !rt.isRevoked())
            .map(rt -> {
                // Update last used timestamp
                rt.setLastUsedAt(Instant.now());
                return refreshTokenRepository.save(rt);
            });
    }
    
    /**
     * Revokes a specific refresh token (logout from one device).
     */
    @Transactional
    public void revokeRefreshToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }
    
    /**
     * Revokes all refresh tokens for a user (logout from all devices).
     */
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.deleteByUser(user);
    }
    
    /**
     * Cleanup: Deletes expired and revoked tokens (run periodically).
     */
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked();
    }
}
