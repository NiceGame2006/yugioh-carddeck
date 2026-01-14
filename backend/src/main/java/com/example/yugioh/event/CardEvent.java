package com.example.yugioh.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event fired when card operations occur.
 * 
 * Event Pattern:
 * - Decouples card operations from side effects (logging, notifications, auditing)
 * - Publishers: CardController (create/update/delete card endpoints)
 * - Listeners: CardEventListener handles events asynchronously
 * 
 * Flow:
 * 1. CardController publishes: eventPublisher.publishEvent(new CardEvent(this, "Card created: " + cardName))
 * 2. Spring delivers event to all @EventListener methods
 * 3. CardEventListener.handleCardEvent() logs to console
 * 4. Could add: email notifications, audit logs, webhooks, search index updates
 * 
 * Benefits:
 * - Non-blocking: Listeners run asynchronously (@Async)
 * - Extensible: Add new listeners without modifying CardController
 * - Testable: Can verify events are published without side effects
 * 
 * Alternative Approaches:
 * - Kafka: For cross-service communication (already used for background jobs)
 * - Events: For in-process, local notifications (this class)
 */
public class CardEvent extends ApplicationEvent {
    private final String message;
    public CardEvent(Object source, String message) {
        super(source);
        this.message = message;
    }
    public String getMessage() {
        return message;
    }
}
