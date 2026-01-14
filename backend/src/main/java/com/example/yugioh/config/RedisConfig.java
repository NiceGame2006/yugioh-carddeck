package com.example.yugioh.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis configuration for caching, distributed locking, and message queues.
 * 
 * Redis Use Cases in This Application:
 * 
 * 1. Caching (@Cacheable) - Read performance optimization
 *    - Card data (14K+ cards, infrequent changes)
 *    - Paginated search results
 *    - Cache TTL: 60 minutes (configurable per cache if needed)
 *    - Eviction: Manual via /api/cards/cache/clear or @CacheEvict on updates
 * 
 * 2. Distributed Locking (DistributedLockService) - Concurrency control
 *    - Prevent duplicate deck creation
 *    - Enforce "max 3 copies per card" rule
 *    - Lock TTL: 5-10 seconds (auto-expires to prevent deadlock)
 * 
 * 3. Rate Limiting (Bucket4j + Redis) - DDoS protection
 *    - Token bucket algorithm stored in Redis
 *    - Distributed: works across multiple backend instances
 *    - Limits: Login 5/min, Search 20/min, General 100/min
 * 
 * 4. Message Queues (MessageQueueService) - Background job processing
 *    - Queue: yugioh:queue:{queueName}
 *    - Used for: Card CRUD notifications, cache operations
 *    - Processed by: BackgroundJobService every 5 seconds
 * 
 * Serialization Strategy:
 * - Keys: StringRedisSerializer (human-readable in redis-cli)
 * - Values: GenericJackson2JsonRedisSerializer (JSON for complex objects)
 * - Benefits: Easy debugging, cross-language compatibility
 * 
 * Why Redis?
 * - In-memory: Sub-millisecond reads (vs ~10ms database)
 * - Distributed: Shared state across multiple backend instances
 * - Atomic operations: SETNX for locking, INCR for counters
 * - Persistence: Survives application restarts (RDB/AOF snapshots)
 * 
 * Connection:
 * - Auto-configured by Spring Boot from application.properties
 * - spring.data.redis.host=redis, spring.data.redis.port=6379
 * - Failover: Application degrades gracefully if Redis is down (fail-open locks)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .entryTtl(Duration.ofMinutes(60));

        // All caches use default config (60 min TTL)
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .transactionAware()
                .build();
    }
}