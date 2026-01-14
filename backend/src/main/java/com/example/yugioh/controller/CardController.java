package com.example.yugioh.controller;

import com.example.yugioh.dto.PaginatedResponse;
import com.example.yugioh.dto.ResponseEnvelope;
import com.example.yugioh.event.CardEvent;
import com.example.yugioh.model.Card;
import com.example.yugioh.repository.DeckRepository;
import com.example.yugioh.service.BackgroundJobService;
import com.example.yugioh.service.CardBatchJobService;
import com.example.yugioh.service.CardCacheService;
import com.example.yugioh.service.CardDataLoader;
import com.example.yugioh.service.MessageQueueService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private static final Logger logger = LoggerFactory.getLogger(CardController.class);
    private final CardDataLoader cardDataLoader;
    private final DeckRepository deckRepository;
    private final CardBatchJobService cardBatchJobService;
    private final CardCacheService cardCacheService;
    private final MessageQueueService messageQueueService;
    private final BackgroundJobService backgroundJobService;
    private final ApplicationEventPublisher eventPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CardController(CardDataLoader cardDataLoader, 
                          DeckRepository deckRepository,
                          CardBatchJobService cardBatchJobService,
                          CardCacheService cardCacheService,
                          MessageQueueService messageQueueService,
                          BackgroundJobService backgroundJobService,
                          ApplicationEventPublisher eventPublisher,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.cardDataLoader = cardDataLoader;
        this.deckRepository = deckRepository;
        this.cardBatchJobService = cardBatchJobService;
        this.cardCacheService = cardCacheService;
        this.messageQueueService = messageQueueService;
        this.backgroundJobService = backgroundJobService;
        this.eventPublisher = eventPublisher;
        this.kafkaTemplate = kafkaTemplate;
    }

    // === Background / Batch / Loader Endpoints ===
    @PostMapping("/run-batch-job")
    public ResponseEntity<ResponseEnvelope<Object>> runBatchJob() {
        cardBatchJobService.runBatchJob(() -> logger.info("[BatchJob] Running simple batch job in background: {}", Thread.currentThread().getName()));
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Simple batch job started");
        return ResponseEntity.ok(env);
    }
    
    @PostMapping("/batch/statistics")
    public ResponseEntity<ResponseEnvelope<Object>> generateStatistics() {
        cardBatchJobService.generateCardStatistics();
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Card statistics generation started in background");
        return ResponseEntity.ok(env);
    }
    
    // Manual cache warmup - preloads Redis with frequently accessed data (count, first 5 pages)
    // Call after: app startup, cache clear, bulk reload, or before high traffic to ensure fast response times
    @PostMapping("/batch/warmup-cache")
    public ResponseEntity<ResponseEnvelope<Object>> warmupCache() {
        cardBatchJobService.warmupCaches();
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Cache warmup started in background");
        return ResponseEntity.ok(env);
    }

    // === Events & Messaging ===
    @PostMapping("/publish-event")
    public ResponseEntity<ResponseEnvelope<Object>> publishEvent() {
        eventPublisher.publishEvent(new CardEvent(this, "Hello from CardEvent!"));
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Event published");
        return ResponseEntity.ok(env);
    }

    @PostMapping("/send-kafka")
    public ResponseEntity<ResponseEnvelope<Object>> sendKafkaMessage() {
        kafkaTemplate.send("card-topic", "Hello from Kafka!");
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Kafka message sent");
        return ResponseEntity.ok(env);
    }

    // === Data reload (async) ===
    @PostMapping("/async-reload")
    public ResponseEntity<ResponseEnvelope<Object>> asyncReloadCards() {
        cardDataLoader.asyncLoadCards();
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Card reload started in background");
        return ResponseEntity.accepted().body(env);
    }

    // === Read (CRUD) endpoints ===
    @GetMapping
    public ResponseEntity<ResponseEnvelope<PaginatedResponse<? extends Object>>> getAllCards(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String query) {
        
        int pageNum = page != null ? page : 0;
        // Default page size is 20 if not specified
        int sizeNum = size != null ? size : 20;
        // Maximum page size is capped at 200 to prevent excessive load and memory usage
        sizeNum = Math.min(sizeNum, 200);
        
        // Custom sort is applied in repository query methods
        Pageable pageable = PageRequest.of(pageNum, sizeNum);
        PaginatedResponse<? extends Object> paginated;
        
        // Performance optimization: search queries bypass cache (too many combinations to cache efficiently),
        // while paginated browsing uses Redis cache (any accessed page cached; warmup pre-loads pages 0-4)
        if (query != null && !query.trim().isEmpty()) {
            // Service layer handles database query for dynamic search results
            paginated = cardCacheService.searchCards(query.trim(), pageable);
        } else {
            // Service layer handles cached query for static pagination (key: 'page_X_size_Y')
            paginated = cardCacheService.getAllCards(pageable);
        }
        
        ResponseEnvelope<PaginatedResponse<? extends Object>> env = ResponseEnvelope.success("Cards fetched", paginated);
        return ResponseEntity.ok(env);
    }

    // Fetch single card by name using query parameter (avoids path encoding issues with slashes eg. D/D)
    @GetMapping("/by-name")
    public ResponseEntity<ResponseEnvelope<Card>> getCardByName(@RequestParam String name) {
        return cardCacheService.getCardByName(name)
            .map(card -> {
                ResponseEnvelope<Card> env = ResponseEnvelope.success("Card fetched", card);
                return ResponseEntity.ok(env);
            })
            .orElseGet(() -> {
                ResponseEnvelope<Card> env = ResponseEnvelope.failed("Card not found");
                return ResponseEntity.status(404).body(env);
            });
    }

    // Legacy endpoint with path variable (kept for backward compatibility, but may fail with slashes eg. D/D)
    @GetMapping("/{name}")
    public ResponseEntity<ResponseEnvelope<Card>> getCardByNameLegacy(@PathVariable String name) {
        return cardCacheService.getCardByName(name)
            .map(card -> {
                ResponseEnvelope<Card> env = ResponseEnvelope.success("Card fetched", card);
                return ResponseEntity.ok(env);
            })
            .orElseGet(() -> {
                ResponseEnvelope<Card> env = ResponseEnvelope.failed("Card not found");
                return ResponseEntity.status(404).body(env);
            });
    }

    // === Write (CRUD) endpoints ===
    @PostMapping
    public ResponseEntity<ResponseEnvelope<Card>> createCard(@Valid @RequestBody Card card) {
        Card savedCard = cardCacheService.saveCard(card);

        // Queue background job for post-creation tasks
        backgroundJobService.queueCardOperation("CARD_CREATED", card.getName(), savedCard);

        // Queue notification
        backgroundJobService.queueNotification("SYSTEM", "New card created: " + card.getName());

        ResponseEnvelope<Card> env = ResponseEnvelope.success("Card created", savedCard);
        return ResponseEntity.status(201).body(env);
    }

    @PutMapping("/{name}")
    public ResponseEntity<ResponseEnvelope<Card>> updateCard(@PathVariable String name, @Valid @RequestBody Card card) {
        card.setName(name);
        Card updatedCard = cardCacheService.saveCard(card);

        // Queue background job for post-update tasks
        backgroundJobService.queueCardOperation("CARD_UPDATED", name, updatedCard);

        // Queue notification
        backgroundJobService.queueNotification("SYSTEM", "Card updated: " + name);

        ResponseEnvelope<Card> env = ResponseEnvelope.success("Card updated", updatedCard);
        return ResponseEntity.ok(env);
    }

    @PatchMapping("/{name}")
    public ResponseEntity<ResponseEnvelope<Card>> patchCard(@PathVariable String name, @Valid @RequestBody Card card) {
        return cardCacheService.getCardByName(name).map(existing -> {
            if (card.getDescription() != null) existing.setDescription(card.getDescription());
            if (card.getHumanReadableCardType() != null) existing.setHumanReadableCardType(card.getHumanReadableCardType());
            if (card.getRace() != null) existing.setRace(card.getRace());
            if (card.getAttribute() != null) existing.setAttribute(card.getAttribute());

            Card updatedCard = cardCacheService.saveCard(existing);

            // Queue background job for post-update tasks
            backgroundJobService.queueCardOperation("CARD_UPDATED", name, updatedCard);

            // Queue notification
            backgroundJobService.queueNotification("SYSTEM", "Card patched: " + name);

            ResponseEnvelope<Card> env = ResponseEnvelope.success("Card patched", updatedCard);
            return ResponseEntity.ok(env);
        }).orElseGet(() -> {
            ResponseEnvelope<Card> env = ResponseEnvelope.failed("Card not found");
            return ResponseEntity.status(404).body(env);
        });
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<ResponseEnvelope<Object>> deleteCard(@PathVariable String name) {
        if (!cardCacheService.getCardByName(name).isPresent()) {
            ResponseEnvelope<Object> env = ResponseEnvelope.failed("Card not found");
            return ResponseEntity.status(404).body(env);
        }
        
        // Check if card is used in any deck
        if (deckRepository.existsByCardName(name)) {
            ResponseEnvelope<Object> env = ResponseEnvelope.failed("Cannot delete card: it is currently used in one or more decks. Remove it from all decks first.");
            return ResponseEntity.status(409).body(env);
        }
        
        cardCacheService.deleteCard(name);

        // Queue background job for post-deletion tasks
        backgroundJobService.queueCardOperation("CARD_DELETED", name, null);

        // Queue notification
        backgroundJobService.queueNotification("SYSTEM", "Card deleted: " + name);

        ResponseEnvelope<Object> env = ResponseEnvelope.success("Card deleted");
        return ResponseEntity.ok(env);
    }
    
    // === Redis and Message Queue Management Endpoints ===
    @PostMapping("/cache/clear")
    public ResponseEntity<ResponseEnvelope<Object>> clearCache() {
        cardCacheService.clearAllCaches();
        ResponseEnvelope<Object> env = ResponseEnvelope.success("All caches cleared successfully");
        return ResponseEntity.ok(env);
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCards", cardCacheService.getTotalCardCount());
        stats.put("cacheHit", cardCacheService.isCountCached());
        ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("Cache stats fetched", stats);
        return ResponseEntity.ok(env);
    }
    
    @PostMapping("/queue/{queueName}/send")
    public ResponseEntity<ResponseEnvelope<Object>> sendToQueue(@PathVariable String queueName, @RequestBody Map<String, Object> message) {
        messageQueueService.enqueue(queueName, message);
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Message sent to queue " + queueName);
        return ResponseEntity.ok(env);
    }

    @GetMapping("/queue/{queueName}/peek")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> peekQueue(@PathVariable String queueName) {
        List<Object> messages = messageQueueService.peekQueue(queueName);
        Map<String, Object> response = new HashMap<>();
        response.put("queueName", queueName);
        response.put("messages", messages);
        response.put("count", messages.size());
        ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("Queue peeked", response);
        return ResponseEntity.ok(env);
    }

    @GetMapping("/queue/{queueName}/size")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> getQueueSize(@PathVariable String queueName) {
        Long size = messageQueueService.getQueueSize(queueName);
        Map<String, Object> response = new HashMap<>();
        response.put("queueName", queueName);
        response.put("size", size);
        ResponseEnvelope<Map<String, Object>> env = ResponseEnvelope.success("Queue size fetched", response);
        return ResponseEntity.ok(env);
    }

    @PostMapping("/queue/{queueName}/clear")
    public ResponseEntity<ResponseEnvelope<Object>> clearQueue(@PathVariable String queueName) {
        messageQueueService.clearQueue(queueName);
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Queue " + queueName + " cleared successfully");
        return ResponseEntity.ok(env);
    }
    
    @PostMapping("/notification/send")
    public ResponseEntity<ResponseEnvelope<Object>> sendNotification(@RequestBody Map<String, String> request) {
        String type = request.getOrDefault("type", "SYSTEM");
        String content = request.getOrDefault("content", "Test notification");
        backgroundJobService.queueNotification(type, content);
        ResponseEnvelope<Object> env = ResponseEnvelope.success("Notification queued successfully");
        return ResponseEntity.ok(env);
    }
}
