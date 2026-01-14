package com.example.yugioh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Distributed lock service using Redis SETNX (SET if Not eXists).
 * Prevents race conditions in concurrent operations across multiple server instances.
 * 
 * Use Cases:
 * - Prevent duplicate deck creation when user double-clicks
 * - Prevent violating "max 3 copies" rule when adding cards concurrently
 * - Ensure only one request modifies a resource at a time
 * 
 * How SETNX Works:
 * 1. Try to set key with value (if key doesn't exist)
 * 2. If success → Lock acquired (return true)
 * 3. If fail → Someone else has lock (return false)
 * 4. Lock auto-expires after timeout (prevents deadlock if server crashes)
 * 
 * Example Usage:
 * <pre>
 * String lockKey = "lock:deck:" + deckId;
 * if (lockService.acquireLock(lockKey, 5)) {
 *     try {
 *         // Critical section - only one request executes this
 *         modifyDeck();
 *     } finally {
 *         lockService.releaseLock(lockKey);
 *     }
 * } else {
 *     return "Resource is locked, try again";
 * }
 * </pre>
 */
@Service
public class DistributedLockService {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockService.class);
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LOCK_PREFIX = "lock:";
    
    public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * Attempts to acquire a distributed lock using Redis SETNX.
     * 
     * @param lockKey Unique identifier for the lock (e.g., "deck:123" or "user:john:create_deck")
     * @param timeoutSeconds How long lock stays locked before auto-expiring (prevents deadlock)
     * @return true if lock acquired, false if someone else has the lock
     */
    public boolean acquireLock(String lockKey, long timeoutSeconds) {
        try {
            String fullKey = LOCK_PREFIX + lockKey;
            // SETNX: SET if Not eXists
            // Returns true if key was set (lock acquired)
            // Returns false if key already exists (someone else has lock)
            Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, "locked", timeoutSeconds, TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(success)) {
                logger.debug("Lock acquired: {}", lockKey);
                return true;
            } else {
                logger.debug("Lock already held by another process: {}", lockKey);
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to acquire lock {}: {}", lockKey, e.getMessage());
            // If Redis is down, fail open (allow operation) to avoid blocking all requests
            return true;
        }
    }
    
    /**
     * Releases a distributed lock by deleting the Redis key.
     * Always call this in a finally block to ensure lock is released.
     * 
     * @param lockKey The lock identifier to release
     */
    public void releaseLock(String lockKey) {
        try {
            String fullKey = LOCK_PREFIX + lockKey;
            Boolean deleted = redisTemplate.delete(fullKey);
            if (Boolean.TRUE.equals(deleted)) {
                logger.debug("Lock released: {}", lockKey);
            } else {
                logger.debug("Lock key not found (may have auto-expired): {}", lockKey);
            }
        } catch (Exception e) {
            logger.error("Failed to release lock {}: {}", lockKey, e.getMessage());
        }
    }
    
    /**
     * Convenience method for executing code with automatic lock management.
     * Acquires lock, executes action, and releases lock in finally block.
     * 
     * @param lockKey The lock identifier
     * @param timeoutSeconds Lock expiration time
     * @param action The code to execute while holding the lock
     * @return true if lock acquired and action executed, false if lock not acquired
     */
    public boolean executeWithLock(String lockKey, long timeoutSeconds, Runnable action) {
        if (acquireLock(lockKey, timeoutSeconds)) {
            try {
                action.run();
                return true;
            } finally {
                releaseLock(lockKey);
            }
        }
        return false;
    }
}
