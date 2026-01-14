package com.example.yugioh.service;

import com.example.yugioh.model.Card;
import com.example.yugioh.model.Archetype;
import com.example.yugioh.repository.CardRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.CommandLineRunner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Loads ~13k cards from YGOPro API - runs on startup if DB empty, or manually via async endpoint
@Service
public class CardDataLoader implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(CardDataLoader.class);
    private static final int BATCH_SIZE = 100;
    private static final String API_URL = "https://db.ygoprodeck.com/api/v7/cardinfo.php";
    
    private final CardRepository cardRepository;
    private final ArchetypeService archetypeService;
    private final RestTemplate restTemplate;

    public CardDataLoader(CardRepository cardRepository, ArchetypeService archetypeService, RestTemplate restTemplate) {
        this.cardRepository = cardRepository;
        this.archetypeService = archetypeService;
        this.restTemplate = restTemplate;
    }

    // Called by: CardController.asyncReloadCards() via POST /api/cards/async-reload
    @Async
    public CompletableFuture<Void> asyncLoadCards() {
        logger.info("[Async] Reloading cards in background thread: {}", Thread.currentThread().getName());
        try {
            loadCardsFromAPI("Async");
            logger.info("[Async] Async reload completed.");
        } catch (Exception e) {
            logger.error("[Async] Error during async card reload: {}", e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    // Called by: Spring Boot on application startup (CommandLineRunner) - only loads if DB is empty
    @Override
    public void run(String... args) {
        if (cardRepository.count() == 0) {
            logger.info("Loading Yu-Gi-Oh cards from API...");
            try {
                int total = loadCardsFromAPI("Startup");
                logger.info("Saved {} cards total.", total);
            } catch (Exception e) {
                logger.error("Error loading cards on startup: {}", e.getMessage(), e);
            }
        }
    }

    // Fetches cards from API, ensures archetypes, and processes card data
    private int loadCardsFromAPI(String context) throws Exception {
        String response = restTemplate.getForObject(API_URL, String.class);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);
        JsonNode data = root.get("data");

        if (data != null && data.isArray()) {
            Set<String> names = new HashSet<>();
            for (JsonNode node : data) {
                String archetypeName = node.path("archetype").asText(null);
                if (archetypeName != null && !archetypeName.trim().isEmpty()) names.add(archetypeName);
            }
            Map<String, Archetype> archetypeMap = archetypeService.ensureArchetypes(names);
            return processCardData(data, archetypeMap, context);
        }
        
        return 0;
    }

    // Parses JSON card data and saves to database in batches of 100
    private int processCardData(JsonNode data, Map<String, Archetype> archetypeMap, String context) {
        List<Card> cards = new ArrayList<>();
        Iterator<JsonNode> it = data.elements();
        int count = 0;

        while (it.hasNext()) {
            JsonNode node = it.next();
            Card card = new Card();
            String cardName = node.path("name").asText();
            String description = node.path("desc").asText();
            
            card.setName(cardName);
            card.setHumanReadableCardType(node.path("type").asText());
            card.setDescription(description);
            card.setRace(node.path("race").asText());
            card.setAttribute(node.path("attribute").asText(null));

            String archetypeName = node.path("archetype").asText(null);
            if (archetypeName != null && !archetypeName.trim().isEmpty()) {
                Archetype archetype = archetypeMap.get(archetypeName);
                card.setArchetype(archetype);
                if (archetype == null) {
                    logger.warn("{}: Archetype not found for card {}: {}", context, card.getName(), archetypeName);
                }
            }

            cards.add(card);
            count++;

            if (count % BATCH_SIZE == 0) {
                cardRepository.saveAll(cards);
                cards.clear();
                logger.info("{}: Saved {} cards...", context, count);
            }
        }

        if (!cards.isEmpty()) {
            cardRepository.saveAll(cards);
        }

        return count;
    }
}
