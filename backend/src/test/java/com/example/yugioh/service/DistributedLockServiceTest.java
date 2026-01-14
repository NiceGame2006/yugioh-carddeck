package com.example.yugioh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistributedLockService.
 * Tests Redis SETNX lock logic without connecting to real Redis.
 * 
 * Verifies that:
 * - Locks are acquired correctly with SETNX
 * - Locks expire automatically (prevents deadlock)
 * - Locks are released properly
 * - Finally block always executes (no lock leaks)
 */
class DistributedLockServiceTest {
    
    private DistributedLockService lockService;
    private RedisTemplate<String, Object> mockRedisTemplate;
    private ValueOperations<String, Object> mockValueOps;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // Create mock Redis template and its value operations
        mockRedisTemplate = mock(RedisTemplate.class);
        mockValueOps = mock(ValueOperations.class);
        
        // Tell mock RedisTemplate to return mock ValueOperations
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        
        // Create service with mock Redis
        lockService = new DistributedLockService(mockRedisTemplate);
    }
    
    @Test
    @DisplayName("Acquire lock when available should return true")
    void testAcquireLock_WhenAvailable_ShouldReturnTrue() {
        // Arrange: SETNX succeeds (returns true = key was set, lock acquired)
        when(mockValueOps.setIfAbsent(
            eq("lock:test-key"), 
            eq("locked"), 
            eq(10L), 
            eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        
        // Act: Try to acquire lock
        boolean acquired = lockService.acquireLock("test-key", 10);
        
        // Assert: Should succeed
        assertTrue(acquired, "Should acquire lock when available");
        
        // Verify SETNX was called with correct parameters
        verify(mockValueOps).setIfAbsent("lock:test-key", "locked", 10L, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("Acquire lock when already locked should return false")
    void testAcquireLock_WhenAlreadyLocked_ShouldReturnFalse() {
        // Arrange: SETNX fails (returns false = key exists, another process has lock)
        when(mockValueOps.setIfAbsent(
            eq("lock:test-key"), 
            eq("locked"), 
            eq(10L), 
            eq(TimeUnit.SECONDS)
        )).thenReturn(false);
        
        // Act: Try to acquire lock
        boolean acquired = lockService.acquireLock("test-key", 10);
        
        // Assert: Should fail (another process has lock)
        assertFalse(acquired, "Should not acquire lock when already locked by another process");
    }
    
    @Test
    @DisplayName("Release lock should delete Redis key")
    void testReleaseLock_ShouldDeleteRedisKey() {
        // Act: Release lock
        lockService.releaseLock("test-key");
        
        // Assert: Verify DELETE was called on Redis key
        verify(mockRedisTemplate).delete("lock:test-key");
    }
    
    @Test
    @DisplayName("Execute with lock should release lock in finally block")
    void testExecuteWithLock_ShouldReleaseInFinally() {
        // Arrange: Lock acquisition succeeds
        when(mockValueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class)))
            .thenReturn(true);
        
        // Act: Execute action with lock
        Runnable action = mock(Runnable.class);
        lockService.executeWithLock("test-key", 5, action);
        
        // Assert: Action was executed
        verify(action).run();
        
        // Assert: Lock was released (DELETE called)
        verify(mockRedisTemplate).delete("lock:test-key");
    }
    
    @Test
    @DisplayName("Execute with lock should release even if action throws exception")
    void testExecuteWithLock_ShouldReleaseEvenOnException() {
        // Arrange: Lock acquisition succeeds, but action throws exception
        when(mockValueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class)))
            .thenReturn(true);
        
        Runnable actionThatThrows = () -> {
            throw new RuntimeException("Simulated error in action");
        };
        
        // Act & Assert: Exception should propagate
        assertThrows(RuntimeException.class, () -> {
            lockService.executeWithLock("test-key", 5, actionThatThrows);
        });
        
        // Assert: Lock was still released despite exception (finally block executed)
        verify(mockRedisTemplate).delete("lock:test-key");
    }
    
    @Test
    @DisplayName("Execute with lock should not release if lock not acquired")
    void testExecuteWithLock_LockNotAcquired_ShouldNotExecuteAction() {
        // Arrange: Lock acquisition fails (another process has lock)
        when(mockValueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class)))
            .thenReturn(false);
        
        // Act: Try to execute action
        Runnable action = mock(Runnable.class);
        lockService.executeWithLock("test-key", 5, action);
        
        // Assert: Action should NOT be executed (lock wasn't acquired)
        verify(action, never()).run();
        
        // Assert: DELETE should not be called (we don't own the lock)
        verify(mockRedisTemplate, never()).delete(anyString());
    }
    
    @Test
    @DisplayName("Acquire lock with Redis down should return true (fail-open)")
    void testAcquireLock_RedisDown_ShouldReturnTrue() {
        // Arrange: Redis throws exception (server down)
        when(mockValueOps.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class)))
            .thenThrow(new RuntimeException("Redis connection failed"));
        
        // Act: Try to acquire lock
        boolean acquired = lockService.acquireLock("test-key", 10);
        
        // Assert: Should return true (fail-open strategy = allow operation when Redis unavailable)
        assertTrue(acquired, "Should return true (fail-open) when Redis is down to avoid blocking all requests");
    }
}
