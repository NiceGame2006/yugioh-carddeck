package com.example.yugioh.service;

import com.example.yugioh.model.Card;
import com.example.yugioh.model.Archetype;
import com.example.yugioh.repository.CardRepository;
import com.example.yugioh.repository.ArchetypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import com.example.yugioh.dto.PaginatedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Map;
import java.util.Collections;

// Card service with Redis caching for read-heavy workloads (14K+ cards)
// Cache types: individual cards (by name), paginated results (by page/size), count
// Manages card-archetype relationships and automatically cleans up orphaned archetypes
@Service
public class CardCacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CardCacheService.class);
    private final CardRepository cardRepository;
    private final ArchetypeService archetypeService;
    private final CacheManager cacheManager;
    private final ArchetypeRepository archetypeRepository;

    public CardCacheService(CardRepository cardRepository, ArchetypeService archetypeService, 
                            CacheManager cacheManager, ArchetypeRepository archetypeRepository) {
        this.cardRepository = cardRepository;
        this.archetypeService = archetypeService;
        this.cacheManager = cacheManager;
        this.archetypeRepository = archetypeRepository;
    }
    
    // === Read Operations ===
    
    // Fetch individual card by name with caching
    // Cache key: card name (e.g., "Dark Magician")
    // Used by: CardController.getCardByName()
    @Cacheable(value = "cards", key = "#name")
    public Optional<Card> getCardByName(String name) {
        logger.info("Cache miss - fetching card '{}' from database", name);
        return cardRepository.findById(name);
    }
    
    // Fetch paginated card list with caching
    // Cache key: 'page_X_size_Y' (e.g., 'page_0_size_20')
    // Used by: CardController.getAllCards() when no search query provided
    @Cacheable(value = "cards", key = "'page_' + #pageable.pageNumber + '_size_' + #pageable.pageSize")
    public PaginatedResponse<Card> getAllCards(Pageable pageable) {
        logger.info("Cache miss - fetching page {} size {} from database", 
                   pageable.getPageNumber(), pageable.getPageSize());
        // Use custom sort query instead of findAll to apply complex sorting logic
        Page<Card> result = cardRepository.findAllWithCustomSort(pageable);
        return PaginatedResponse.fromPage(result);
    }
    
    // Search cards by query - NOT cached due to dynamic nature
    // Used by: CardController.getAllCards() when search query provided
    public PaginatedResponse<Card> searchCards(String query, Pageable pageable) {
        logger.info("Searching cards with query '{}' page {} size {}", query, pageable.getPageNumber(), pageable.getPageSize());
        Page<Card> result = cardRepository.searchCards(query, pageable);
        return PaginatedResponse.fromPage(result);
    }
    
    // === Write Operations ===
    
    // Save/update card then clear all caches to prevent stale data
    // Resolves archetype references before saving
    // Used by: CardController.createCard(), CardController.updateCard()
    public Card saveCard(Card card) {
        logger.info("Saving card '{}'", card.getName());
        if (card.getArchetype() != null && card.getArchetype().getName() != null) {
            String archetypeName = card.getArchetype().getName();
            Map<String, Archetype> map = archetypeService.ensureArchetypes(Collections.singleton(archetypeName));
            Archetype resolved = map.get(archetypeName);
            if (resolved != null) {
                card.setArchetype(resolved);
            }
        }

        Card savedCard = cardRepository.save(card);

        // Clear all caches to prevent stale pagination/count data
        // Trade-off: Clears individual card caches too, but ensures consistency
        // Acceptable because saves are infrequent and caches rebuild quickly
        clearAllCaches();

        return savedCard;
    }
    
    // Delete card then clear all caches to prevent stale data
    // Automatically deletes orphaned archetypes (no remaining cards reference them)
    // Used by: CardController.deleteCard()
    @Transactional
    public void deleteCard(String name) {
        logger.info("Deleting card '{}'", name);

        Optional<Card> maybe = cardRepository.findById(name);
        String archetypeName = null;
        if (maybe.isPresent() && maybe.get().getArchetype() != null) {
            archetypeName = maybe.get().getArchetype().getName();
        }

        cardRepository.deleteById(name);

        // Clear all caches to prevent stale pagination/count data
        clearAllCaches();

        if (archetypeName != null) {
            try {
                long remaining = cardRepository.countByArchetypeName(archetypeName);
                if (remaining == 0) {
                    Archetype a = archetypeRepository.findByName(archetypeName);
                    if (a != null) {
                        archetypeRepository.delete(a);
                        logger.info("Deleted orphan archetype '{}' after card deletion", archetypeName);
                    }
                }
            } catch (DataIntegrityViolationException ex) {
                logger.warn("Could not delete archetype '{}' due to concurrent references: {}", archetypeName, ex.getMessage());
            } catch (Exception ex) {
                logger.warn("Unexpected error while attempting to delete archetype '{}': {}", archetypeName, ex.getMessage());
            }
        }
    }
    
    // === Count Operations ===
    
    // Get total card count with caching (used for pagination calculations)
    // Cache key: 'count'
    // Used by: CardBatchJobService.warmupCaches(), CardController.getCacheStats()
    @Cacheable(value = "cards", key = "'count'")
    public long getTotalCardCount() {
        logger.info("Cache miss - fetching total card count from database");
        return cardRepository.count();
    }
    
    // Check if count is currently cached (for monitoring/debugging)
    // Used by: CardController.getCacheStats()
    public boolean isCountCached() {
        Cache cache = cacheManager.getCache("cards");
        if (cache == null) return false;
        Cache.ValueWrapper wrapper = cache.get("count");
        return wrapper != null;
    }
    
    // === Utility Operations ===
    
    // Clear ALL cache entries: individual cards, pagination results, and count
    // Caches rebuild automatically: warmup job handles common cases, on-demand for others
    // Used by: saveCard(), deleteCard(), CardController.clearCache(), BackgroundJobService
    public void clearAllCaches() {
        logger.info("Clearing all card caches (full eviction)");
        Cache cache = cacheManager.getCache("cards");
        if (cache != null) {
            cache.clear();
        } else {
            logger.warn("Cache 'cards' not found when attempting to clear all caches");
        }
    }
}