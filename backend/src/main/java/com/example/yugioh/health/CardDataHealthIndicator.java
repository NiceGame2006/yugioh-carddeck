package com.example.yugioh.health;

import com.example.yugioh.repository.CardRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator that reports DOWN until the application has loaded a minimum number of cards.
 * This makes /actuator/health suitable as a readiness probe for Kubernetes.
 */
@Component("cardDataHealthIndicator")
public class CardDataHealthIndicator implements HealthIndicator {

    private final CardRepository cardRepository;

    @Value("${cards.health.min-count:1}")
    private int minCardCount;

    public CardDataHealthIndicator(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    @Override
    public Health health() {
        try {
            long count = cardRepository.count();
            if (count >= minCardCount) {
                return Health.up().withDetail("cardsLoaded", count).build();
            } else {
                return Health.down().withDetail("cardsLoaded", count).withDetail("required", minCardCount).withDetail("reason", "insufficient-cards").build();
            }
        } catch (Exception e) {
            return Health.down().withDetail("exception", e.getMessage()).build();
        }
    }
}
