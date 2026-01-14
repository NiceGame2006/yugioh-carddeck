package com.example.yugioh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for Redis-based message queue operations.
 * Provides FIFO queue functionality with blocking/non-blocking dequeue.
 * Used automatically by BackgroundJobService (card CRUD events) and manually via admin endpoints.
 */
@Service
public class MessageQueueService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageQueueService.class);
    private static final String QUEUE_PREFIX = "yugioh:queue:";
    private static final long DEFAULT_TIMEOUT_SECONDS = 10;
    private final RedisTemplate<String, Object> redisTemplate;

    public MessageQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    public void enqueue(String queueName, Object message) {
        try {
            String queueKey = QUEUE_PREFIX + queueName;
            redisTemplate.opsForList().leftPush(queueKey, message);
            logger.info("Message added to queue {}: {}", queueName, message);
        } catch (Exception e) {
            logger.error("Failed to enqueue message to {}: {}", queueName, e.getMessage());
        }
    }
    
    /**
     * Waits up to 10 seconds for a message to become available.
     * Use for dedicated worker threads that should wait for work instead of spinning.
     * @return message object or null if timeout/error
     */
    public Object dequeue(String queueName) {
        try {
            String queueKey = QUEUE_PREFIX + queueName;
            Object message = redisTemplate.opsForList().rightPop(queueKey, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (message != null) {
                logger.info("Message retrieved from queue {}: {}", queueName, message);
            }
            return message;
        } catch (Exception e) {
            logger.error("Failed to dequeue message from {}: {}", queueName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Returns immediately without waiting - used by BackgroundJobService polling every 5 seconds.
     * Preferred for scheduled tasks to avoid tying up threads.
     * @return message object or null if queue empty/error
     */
    public Object dequeueNonBlocking(String queueName) {
        try {
            String queueKey = QUEUE_PREFIX + queueName;
            Object message = redisTemplate.opsForList().rightPop(queueKey);
            if (message != null) {
                logger.info("Message retrieved from queue {}: {}", queueName, message);
            }
            return message;
        } catch (Exception e) {
            logger.error("Failed to dequeue message from {}: {}", queueName, e.getMessage());
            return null;
        }
    }
    
    public Long getQueueSize(String queueName) {
        try {
            String queueKey = QUEUE_PREFIX + queueName;
            return redisTemplate.opsForList().size(queueKey);
        } catch (Exception e) {
            logger.error("Failed to get queue size for {}: {}", queueName, e.getMessage());
            return 0L;
        }
    }
    
    public List<Object> peekQueue(String queueName) {
        try {
            String queueKey = QUEUE_PREFIX + queueName;
            return redisTemplate.opsForList().range(queueKey, 0, -1);
        } catch (Exception e) {
            logger.error("Failed to peek queue {}: {}", queueName, e.getMessage());
            return List.of();
        }
    }
    
    public void clearQueue(String queueName) {
        try {
            String queueKey = QUEUE_PREFIX + queueName;
            redisTemplate.delete(queueKey);
            logger.info("Queue {} cleared", queueName);
        } catch (Exception e) {
            logger.error("Failed to clear queue {}: {}", queueName, e.getMessage());
        }
    }
}