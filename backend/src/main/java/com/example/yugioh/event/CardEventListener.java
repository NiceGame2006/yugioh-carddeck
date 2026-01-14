package com.example.yugioh.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous listener for card-related application events.
 * 
 * Event Flow:
 * 1. CardController publishes CardEvent when cards are created/updated/deleted
 * 2. Spring automatically delivers event to this listener
 * 3. @Async ensures non-blocking execution (runs in separate thread pool)
 * 4. Currently logs events; can be extended for notifications, auditing, search indexing
 * 
 * Benefits of @Async:
 * - Controller doesn't wait for listener to complete
 * - Faster API response times (e.g., 50ms vs 200ms if sending emails)
 * - Failures in listener don't affect main request
 * 
 * Thread Pool Configuration:
 * - Configured in AppConfig.taskExecutor()
 * - Core pool: 2 threads, Max pool: 5 threads
 * - Prevents listener from blocking main application threads
 * 
 * Potential Extensions:
 * - Send email notifications to admins on card creation
 * - Update full-text search index (Elasticsearch/Solr)
 * - Trigger webhooks for external integrations
 * - Audit log to separate database table
 * - Invalidate CDN cache for card images
 */
@Component
public class CardEventListener {

    private static final Logger logger = LoggerFactory.getLogger(CardEventListener.class);

    /**
     * Handles card events asynchronously (non-blocking).
     * 
     * @Async ensures this runs in background thread pool (from AppConfig.taskExecutor())
     * @EventListener tells Spring to call this when CardEvent is published
     * 
     * @param event The card event containing operation message
     */
    @Async
    @EventListener
    public void handleCardEvent(CardEvent event) {
        logger.info("[EventListener] Received event: {}", event.getMessage());
    }
}
