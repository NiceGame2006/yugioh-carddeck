package com.example.yugioh.service;

import com.example.yugioh.model.Card;
import com.example.yugioh.repository.CardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for running batch operations (statistics generation, cache warmup, custom jobs)
 * All methods are manually triggered via admin endpoints - no automatic scheduling.
 */
@Service
public class CardBatchJobService {
    
    private static final Logger logger = LoggerFactory.getLogger(CardBatchJobService.class);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CardRepository cardRepository;
    private final CardCacheService cardCacheService;

    public CardBatchJobService(CardRepository cardRepository, CardCacheService cardCacheService) {
        this.cardRepository = cardRepository;
        this.cardCacheService = cardCacheService;
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down batch job executor...");
        executor.shutdown();
    }
    
    /**
     * Run a custom batch job
     */
    public void runBatchJob(Runnable job) {
        executor.submit(() -> {
            try {
                logger.info("Starting custom batch job...");
                long startTime = System.currentTimeMillis();
                job.run();
                long endTime = System.currentTimeMillis();
                logger.info("Custom batch job completed in {}ms", (endTime - startTime));
            } catch (Exception e) {
                logger.error("Custom batch job failed: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Batch operation: Generate card statistics
     */
    public void generateCardStatistics() {
        executor.submit(() -> {
            try {
                logger.info("Starting card statistics generation...");
                long startTime = System.currentTimeMillis();
                
                List<Card> cards = cardRepository.findAll();
                
                // Generate statistics
                Map<String, Long> typeStats = cards.stream()
                    .collect(Collectors.groupingBy(
                        card -> card.getHumanReadableCardType() != null ? card.getHumanReadableCardType() : "Unknown",
                        Collectors.counting()
                    ));
                
                Map<String, Long> raceStats = cards.stream()
                    .collect(Collectors.groupingBy(
                        card -> card.getRace() != null ? card.getRace() : "Unknown",
                        Collectors.counting()
                    ));
                
                Map<String, Long> attributeStats = cards.stream()
                    .collect(Collectors.groupingBy(
                        card -> card.getAttribute() != null ? card.getAttribute() : "Unknown",
                        Collectors.counting()
                    ));
                
                // Log statistics
                logger.info("=== CARD STATISTICS ===");
                logger.info("Total Cards: {}", cards.size());
                
                long typeCount = typeStats.size();
                long raceCount = raceStats.size();
                long attributeCount = attributeStats.size();
                
                long endTime = System.currentTimeMillis();
                logger.info("Card statistics generation completed in {}ms - {} types, {} races, {} attributes", 
                           (endTime - startTime), typeCount, raceCount, attributeCount);
                
            } catch (Exception e) {
                logger.error("Card statistics generation failed: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Batch operation: Warm up caches
     */
    public void warmupCaches() {
        executor.submit(() -> {
            try {
                logger.info("Starting cache warmup...");
                long startTime = System.currentTimeMillis();
                
                // Warm up total count cache
                cardCacheService.getTotalCardCount();
                
                // Warm up pagination caches for frequently accessed pages
                for (int page = 0; page < 5; page++) {
                    cardCacheService.getAllCards(PageRequest.of(page, 20));
                }
                
                long endTime = System.currentTimeMillis();
                logger.info("Cache warmup completed in {}ms", (endTime - startTime));
                
            } catch (Exception e) {
                logger.error("Cache warmup failed: {}", e.getMessage(), e);
            }
        });
    }
}
