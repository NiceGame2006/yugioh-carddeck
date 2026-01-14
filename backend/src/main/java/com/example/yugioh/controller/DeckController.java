package com.example.yugioh.controller;

import com.example.yugioh.model.Deck;
import com.example.yugioh.repository.DeckRepository;
import com.example.yugioh.dto.ResponseEnvelope;
import com.example.yugioh.dto.DeckOperationData;
import com.example.yugioh.service.DeckService;
import com.example.yugioh.service.DistributedLockService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/decks")
public class DeckController {
    private final DeckRepository deckRepository;
    private final DeckService deckService;
    private final DistributedLockService lockService;

    public DeckController(DeckRepository deckRepository, DeckService deckService, 
                          DistributedLockService lockService) {
        this.deckRepository = deckRepository;
        this.deckService = deckService;
        this.lockService = lockService;
    }

    /**
     * Checks if the authenticated user has ROLE_ADMIN authority.
     * 
     * @param authentication Spring Security authentication context
     * @return true if user is admin, false otherwise
     */
    private boolean isAdmin(Authentication authentication) {
        return authentication != null && 
            authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Extracts username from authentication context.
     * 
     * @param authentication Spring Security authentication context
     * @return username or null if not authenticated
     */
    private String getUsername(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    /**
     * Checks if the current user can modify the specified deck.
     * 
     * @param deckId Deck ID to check
     * @param authentication Spring Security authentication context
     * @return true if authorized, false otherwise
     */
    private boolean canUserModifyDeck(Long deckId, Authentication authentication) {
        String username = getUsername(authentication);
        return deckService.canModifyDeck(deckId, username, isAdmin(authentication));
    }

    @GetMapping
    public ResponseEntity<ResponseEnvelope<List<Deck>>> getAll(Authentication authentication) {
        // Everyone can see all decks (with owner info), but only owner/admin can edit
        List<Deck> decks = deckRepository.findAll();
        ResponseEnvelope<List<Deck>> env = ResponseEnvelope.success("Decks fetched", decks);
        return ResponseEntity.ok(env);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseEnvelope<Deck>> getById(@PathVariable Long id) {
        return deckRepository.findById(id)
            .map(d -> {
                ResponseEnvelope<Deck> env = ResponseEnvelope.success("Deck fetched", d);
                return ResponseEntity.ok(env);
            })
            .orElseGet(() -> {
                ResponseEnvelope<Deck> env = ResponseEnvelope.failed("Deck not found");
                return ResponseEntity.status(404).body(env);
            });
    }

    /**
     * Creates a new deck with distributed lock to prevent duplicate submissions.
     * 
     * Authorization: Requires authentication (any logged-in user)
     * Rate Limit: 100 requests/minute (general API limit)
     * 
     * Distributed Locking:
     * - Lock key: "user:{username}:create_deck"
     * - Prevents: Double-click, multiple tabs, network retry duplicates
     * - Timeout: 10 seconds (lock auto-expires to prevent deadlock)
     * 
     * Business Rules:
     * - Deck owner set to authenticated username
     * - Deck name sanitized via @PrePersist hook (XSS protection)
     * - No limit on number of decks per user
     * 
     * @param deck Deck data (name required, validated by @Valid)
     * @param authentication Spring Security authentication context
     * @return 201 Created with saved deck, or 409 Conflict if lock acquisition fails
     */
    @PostMapping
    public ResponseEntity<ResponseEnvelope<Deck>> create(@Valid @RequestBody Deck deck, Authentication authentication) {
        // Verify user is authenticated
        if (authentication == null || !authentication.isAuthenticated()) {
            ResponseEnvelope<Deck> env = ResponseEnvelope.failed("Authentication required to create deck");
            return ResponseEntity.status(401).body(env);
        }
        
        String username = authentication.getName();
        String lockKey = "user:" + username + ":create_deck";
        
        // Acquire distributed lock to prevent concurrent deck creation
        // Prevents: double-click, multiple tabs, network retries
        if (!lockService.acquireLock(lockKey, 10)) {
            ResponseEnvelope<Deck> env = ResponseEnvelope.failed(
                "Another deck creation is in progress. Please wait and try again.");
            return ResponseEntity.status(409).body(env);
        }
        
        try {
            deck.setUsername(username);
            Deck saved = deckRepository.save(deck);
            ResponseEnvelope<Deck> env = ResponseEnvelope.success("Deck created", saved);
            return ResponseEntity.status(201).body(env);
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseEnvelope<Deck>> update(@PathVariable Long id, @Valid @RequestBody Deck deck, Authentication authentication) {
        // Check ownership
        if (!canUserModifyDeck(id, authentication)) {
            ResponseEnvelope<Deck> env = ResponseEnvelope.failed("You don't have permission to modify this deck");
            return ResponseEntity.status(403).body(env);
        }
        
        String lockKey = "deck:" + id;
        
        // Acquire distributed lock to prevent concurrent updates
        if (!lockService.acquireLock(lockKey, 5)) {
            ResponseEnvelope<Deck> env = ResponseEnvelope.failed(
                "Deck is being modified by another request. Please try again.");
            return ResponseEntity.status(409).body(env);
        }
        
        try {
            // Preserve the original owner
            Deck existingDeck = deckRepository.findById(id).orElse(null);
            if (existingDeck != null) {
                deck.setUsername(existingDeck.getUsername());
            }
            
            deck.setId(id);
            Deck saved = deckRepository.save(deck);
            ResponseEnvelope<Deck> env = ResponseEnvelope.success("Deck updated", saved);
            return ResponseEntity.ok(env);
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseEnvelope<Object>> deleteDeck(@PathVariable Long id, Authentication authentication) {
        if (!deckRepository.existsById(id)) {
            ResponseEnvelope<Object> env = ResponseEnvelope.failed("Deck not found");
            return ResponseEntity.status(404).body(env);
        }
        
        // Check ownership
        if (!canUserModifyDeck(id, authentication)) {
            ResponseEnvelope<Object> env = ResponseEnvelope.failed("You don't have permission to delete this deck");
            return ResponseEntity.status(403).body(env);
        }
        
        String lockKey = "deck:" + id;
        
        // Acquire distributed lock to prevent concurrent deletion
        if (!lockService.acquireLock(lockKey, 5)) {
            ResponseEnvelope<Object> env = ResponseEnvelope.failed(
                "Deck is being modified by another request. Please try again.");
            return ResponseEntity.status(409).body(env);
        }
        
        try {
            deckRepository.deleteById(id);
            ResponseEnvelope<Object> env = ResponseEnvelope.success("Deck deleted");
            return ResponseEntity.ok(env);
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    @PostMapping("/{deckId}/cards/{cardName}")
    public ResponseEntity<ResponseEnvelope<DeckOperationData>> addCardToDeck(@PathVariable Long deckId, @PathVariable String cardName, Authentication authentication) {
        // Check ownership
        if (!canUserModifyDeck(deckId, authentication)) {
            return ResponseEntity.status(403)
                .body(ResponseEnvelope.failed("You don't have permission to modify this deck"));
        }
        
        ResponseEnvelope<DeckOperationData> result = deckService.addCardToDeck(deckId, cardName);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(400).body(result);
        }
    }

    @DeleteMapping("/{deckId}/cards/{cardName}")
    public ResponseEntity<ResponseEnvelope<DeckOperationData>> removeCardFromDeck(@PathVariable Long deckId, @PathVariable String cardName, Authentication authentication) {
        // Check ownership
        if (!canUserModifyDeck(deckId, authentication)) {
            return ResponseEntity.status(403)
                .body(ResponseEnvelope.failed("You don't have permission to modify this deck"));
        }
        
        ResponseEnvelope<DeckOperationData> result = deckService.removeCardFromDeck(deckId, cardName);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(400).body(result);
        }
    }
}
