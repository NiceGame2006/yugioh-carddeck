package com.example.yugioh.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer for card-related messages from external systems or microservices.
 * 
 * Kafka vs Spring Events vs Redis Queue:
 * - Kafka: Cross-service messaging, durable, scalable, ordered (use for microservices)
 * - Spring Events: In-process, fast, not durable (use for local notifications)
 * - Redis Queue: In-process, simple, less features than Kafka (use for background jobs)
 * 
 * Message Flow:
 * 1. External service or admin publishes to "card-topic" via CardController.publishCardEvent()
 * 2. Kafka brokers store message durably (survives restarts)
 * 3. This consumer receives message via @KafkaListener
 * 4. Currently logs message; can be extended for cross-service data sync
 * 
 * Configuration:
 * - Topic: "card-topic" (configured in application.properties: spring.kafka.bootstrap-servers)
 * - Group ID: "yugioh-group" (multiple instances share work in same consumer group)
 * - Auto-offset: "earliest" (process all messages from beginning on first start)
 * 
 * Use Cases:
 * - Replicate card data to read-only replica database
 * - Sync card catalog to recommendation engine
 * - Trigger analytics pipeline when cards are added
 * - Notify other microservices of card changes
 * 
 * Resiliency:
 * - fail-fast=false: Application starts even if Kafka is down
 * - missing-topics-fatal=false: Tolerates missing topics during startup
 * - Producer retries=3: Retries failed publishes 3 times
 * 
 * This consumer is only enabled when kafka.enabled=true in application properties
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class CardKafkaConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CardKafkaConsumer.class);

    /**
     * Listens to "card-topic" Kafka topic and processes messages.
     * 
     * @KafkaListener automatically polls Kafka broker for new messages
     * Spring Boot manages consumer lifecycle (connect, poll, commit offsets, error handling)
     * 
     * @param message Card operation message (e.g., "Card created: Dark Magician")
     */
    @KafkaListener(topics = "card-topic", groupId = "yugioh-group")
    public void listen(String message) {
        logger.info("[Kafka] Received message: {}", message);
    }
}
