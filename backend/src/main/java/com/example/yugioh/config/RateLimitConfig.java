package com.example.yugioh.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Redis-backed distributed rate limiting using Bucket4j.
 * 
 * Bucket4j Token Bucket Algorithm:
 * - Each user/IP gets a bucket with a fixed capacity (e.g., 5 tokens)
 * - Each request consumes 1 token from the bucket
 * - Tokens refill at a fixed rate (e.g., 5 tokens per minute)
 * - If bucket is empty, request is rejected
 * 
 * Why Redis-backed?
 * - Distributed: Rate limits work across multiple backend instances
 * - Persistent: Limits survive application restarts
 * - Fast: In-memory lookups with O(1) complexity
 * 
 * Alternative Approaches:
 * - In-memory (Bucket4j local): Doesn't work with multiple instances
 * - Database: Too slow for high-frequency rate limit checks
 * - Nginx rate limiting: Works but less flexible for per-user limits
 */
@Configuration
public class RateLimitConfig {
    
    @Value("${spring.data.redis.host}")
    private String redisHost;
    
    @Value("${spring.data.redis.port}")
    private int redisPort;
    
    @Value("${spring.data.redis.password:}")
    private String redisPassword;
    
    /**
     * Creates Redis connection for Bucket4j.
     * Uses Lettuce client (Spring's default Redis client).
     */
    @Bean
    public StatefulRedisConnection<String, byte[]> redisConnection() {
        RedisURI.Builder builder = RedisURI.Builder.redis(redisHost, redisPort);
        
        // Add password if configured (production)
        if (redisPassword != null && !redisPassword.isEmpty()) {
            builder.withPassword(redisPassword.toCharArray());
        }
        
        RedisURI redisUri = builder.build();
        RedisClient redisClient = RedisClient.create(redisUri);
        
        // Use String keys and byte[] values for Bucket4j storage
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        
        return redisClient.connect(codec);
    }
    
    /**
     * Creates ProxyManager for distributed rate limiting.
     * ProxyManager handles storing/retrieving bucket states from Redis.
     */
    @Bean
    public ProxyManager<String> proxyManager(StatefulRedisConnection<String, byte[]> redisConnection) {
        return LettuceBasedProxyManager.builderFor(redisConnection)
            .build();
    }
}
