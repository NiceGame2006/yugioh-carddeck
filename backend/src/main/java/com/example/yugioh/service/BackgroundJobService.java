package com.example.yugioh.service;

import com.example.yugioh.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for automatic background job processing via Redis message queues.
 * Queue operations auto-triggered on card CRUD events.
 */
@Service
public class BackgroundJobService {
    
    private static final Logger logger = LoggerFactory.getLogger(BackgroundJobService.class);
    private final MessageQueueService messageQueueService;
    private final CardCacheService cardCacheService;

    public BackgroundJobService(MessageQueueService messageQueueService, CardCacheService cardCacheService) {
        this.messageQueueService = messageQueueService;
        this.cardCacheService = cardCacheService;
    }
    
    /**
     * Process jobs from the queue every 5 seconds
     */
    @Scheduled(fixedDelay = 5000)
    public void processJobs() {
        processQueue("card-operations");
        processQueue("cache-operations");
        processQueue("notifications");
    }
    
    private void processQueue(String queueName) {
        int processedCount = 0;
        int maxMessagesPerCycle = 10;
        
        while (processedCount < maxMessagesPerCycle) {
            try {
                Object message = messageQueueService.dequeueNonBlocking(queueName);
                if (message == null) {
                    break; // No more messages in queue
                }
                
                logger.info("Processing job from queue {}: {}", queueName, message);
                
                switch (queueName) {
                    case "card-operations":
                        processCardOperation(message);
                        break;
                    case "cache-operations":
                        processCacheOperation(message);
                        break;
                    case "notifications":
                        processNotification(message);
                        break;
                    default:
                        logger.warn("Unknown queue: {}", queueName);
                }
                
                processedCount++;
            } catch (Exception e) {
                logger.error("Error processing queue {}: {}", queueName, e.getMessage());
                break; // Stop processing on error to prevent cascading failures
            }
        }
        
        if (processedCount > 0) {
            logger.debug("Processed {} message(s) from queue {}", processedCount, queueName);
        }
    }
    
    private void processCardOperation(Object message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> operation = (Map<String, Object>) message;
            String type = (String) operation.get("type");
            
            switch (type) {
                case "CARD_CREATED":
                    logger.info("Processing card creation: {}", operation.get("cardName"));
                    // Add any post-creation logic here
                    break;
                case "CARD_UPDATED":
                    logger.info("Processing card update: {}", operation.get("cardName"));
                    // Add any post-update logic here
                    break;
                case "CARD_DELETED":
                    logger.info("Processing card deletion: {}", operation.get("cardName"));
                    // Add any post-deletion logic here
                    break;
                default:
                    logger.warn("Unknown card operation type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error processing card operation: {}", e.getMessage());
        }
    }
    
    private void processCacheOperation(Object message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> operation = (Map<String, Object>) message;
            String type = (String) operation.get("type");
            
            switch (type) {
                case "CLEAR_ALL":
                    logger.info("Clearing all caches");
                    cardCacheService.clearAllCaches();
                    break;
                default:
                    logger.warn("Unknown cache operation type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error processing cache operation: {}", e.getMessage());
        }
    }
    
    private void processNotification(Object message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = (Map<String, Object>) message;
            String type = (String) notification.get("type");
            String content = (String) notification.get("content");
            
            logger.info("Processing notification [{}]: {}", type, content);
            
            // Here you could send emails, push notifications, etc.
            switch (type) {
                case "EMAIL":
                    // Send email notification
                    logger.info("Would send email: {}", content);
                    break;
                case "SYSTEM":
                    // System notification
                    logger.info("System notification: {}", content);
                    break;
                default:
                    logger.warn("Unknown notification type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error processing notification: {}", e.getMessage());
        }
    }
    
    /**
     * Queue a card operation for background processing
     */
    @Async
    public void queueCardOperation(String operationType, String cardName, Card card) {
        try {
            Map<String, Object> operation = Map.of(
                "type", operationType,
                "cardName", cardName,
                "card", card != null ? card : "",
                "timestamp", System.currentTimeMillis()
            );
            
            messageQueueService.enqueue("card-operations", operation);
            logger.info("Queued card operation: {} for card: {}", operationType, cardName);
        } catch (Exception e) {
            logger.error("Failed to queue card operation: {}", e.getMessage());
        }
    }
    
    /**
     * Queue a cache operation for background processing
     */
    @Async
    public void queueCacheOperation(String operationType) {
        try {
            Map<String, Object> operation = Map.of(
                "type", operationType,
                "timestamp", System.currentTimeMillis()
            );
            
            messageQueueService.enqueue("cache-operations", operation);
            logger.info("Queued cache operation: {}", operationType);
        } catch (Exception e) {
            logger.error("Failed to queue cache operation: {}", e.getMessage());
        }
    }
    
    /**
     * Queue a notification for background processing
     */
    @Async
    public void queueNotification(String type, String content) {
        try {
            Map<String, Object> notification = Map.of(
                "type", type,
                "content", content,
                "timestamp", System.currentTimeMillis()
            );
            
            messageQueueService.enqueue("notifications", notification);
            logger.info("Queued notification [{}]: {}", type, content);
        } catch (Exception e) {
            logger.error("Failed to queue notification: {}", e.getMessage());
        }
    }
}