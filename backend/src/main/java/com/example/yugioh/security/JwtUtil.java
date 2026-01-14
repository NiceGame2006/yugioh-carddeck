package com.example.yugioh.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT utility for creating and validating JSON Web Tokens using RSA asymmetric encryption.
 * 
 * JWT Structure:
 * - Header: Algorithm (RS256) and token type
 * - Payload: User data (username, roles, expiration)
 * - Signature: RSA signature using private key
 * 
 * Why RSA (Asymmetric) vs HMAC (Symmetric)?
 * - Private Key: Signs tokens (kept secret on backend only)
 * - Public Key: Verifies tokens (can be shared with other services)
 * - More secure: Even if public key leaks, can't forge tokens
 * - Microservices-ready: Other services can verify without secret key
 * 
 * Why JWT?
 * - Self-contained: All user data in token (no database lookup for access tokens)
 * - Stateless: No session storage needed for access tokens
 * - Scalable: Works across multiple backend instances
 * 
 * Security:
 * - PRIVATE_KEY stored only on server (never exposed to client)
 * - PUBLIC_KEY can be shared (e.g., for microservices or frontend verification)
 * - Client can READ token but can't CREATE valid tokens (needs private key)
 * - Signature verification ensures token hasn't been tampered with
 */
@Component
public class JwtUtil {
    
    @Value("${jwt.private-key:classpath:private_key.pem}")
    private Resource privateKeyResource;
    
    @Value("${jwt.public-key:classpath:public_key.pem}")
    private Resource publicKeyResource;
    
    @Value("${jwt.expiration:900000}") // Default 15 minutes (in milliseconds)
    private long expiration;
    
    private PrivateKey privateKey;
    private PublicKey publicKey;
    
    @PostConstruct
    public void init() throws Exception {
        this.privateKey = loadPrivateKey();
        this.publicKey = loadPublicKey();
    }
    
    private PrivateKey loadPrivateKey() throws Exception {
        String key = new String(privateKeyResource.getInputStream().readAllBytes())
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }
    
    private PublicKey loadPublicKey() throws Exception {
        String key = new String(publicKeyResource.getInputStream().readAllBytes())
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }
    
    /**
     * Generates JWT access token from username and roles using RSA private key.
     * Token contains: username, roles, issued time, expiration time.
     */
    public String generateToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
            .setSubject(username)
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(privateKey, SignatureAlgorithm.RS256) // RSA signing with private key
            .compact();
    }
    
    /**
     * Extracts username from JWT token using RSA public key.
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(publicKey) // RSA verification with public key
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        return claims.getSubject();
    }
    
    /**
     * Extracts roles from JWT token and converts to GrantedAuthority.
     */
    @SuppressWarnings("unchecked")
    public List<GrantedAuthority> getRolesFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
            .setSigningKey(publicKey) // RSA verification with public key
            .build()
            .parseClaimsJws(token)
            .getBody();
        
        List<String> roles = (List<String>) claims.get("roles");
        return roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }
    
    /**
     * Validates JWT token using RSA public key.
     * Checks: signature validity and expiration.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(publicKey) // RSA verification with public key
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false; // Invalid signature or expired
        }
    }
    
    /* ========== OLD HMAC (SYMMETRIC) IMPLEMENTATION (COMMENTED OUT FOR REFERENCE) ==========
     * 
     * This was the previous approach using HMAC-SHA512 symmetric encryption.
     * 
     * @Value("${jwt.secret:my-super-secret-key-change-this-in-production-12345678901234567890}")
     * private String secret;
     * 
     * private Key getSigningKey() {
     *     return Keys.hmacShaKeyFor(secret.getBytes());
     * }
     * 
     * public String generateToken(String username, List<String> roles) {
     *     return Jwts.builder()
     *         .setSubject(username)
     *         .claim("roles", roles)
     *         .setIssuedAt(new Date())
     *         .setExpiration(new Date(System.currentTimeMillis() + expiration))
     *         .signWith(getSigningKey(), SignatureAlgorithm.HS512) // HMAC signing
     *         .compact();
     * }
     * 
     * Validation also used getSigningKey() for symmetric verification.
     * 
     * ========== END OF OLD HMAC IMPLEMENTATION ==========
     */
}
