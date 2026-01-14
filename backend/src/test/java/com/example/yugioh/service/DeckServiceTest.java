package com.example.yugioh.service;

import com.example.yugioh.model.Deck;
import com.example.yugioh.model.Card;
import com.example.yugioh.repository.DeckRepository;
import com.example.yugioh.repository.CardRepository;
import com.example.yugioh.dto.ResponseEnvelope;
import com.example.yugioh.dto.DeckOperationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeckService.
 * Tests business logic in isolation without Spring, database, or Redis.
 * 
 * Uses Mockito to create fake (mock) repositories.
 * This makes tests fast (milliseconds) and focused on logic only.
 */
class DeckServiceTest {
    
    private DeckService deckService;
    private DeckRepository mockDeckRepo;
    private CardRepository mockCardRepo;
    private DistributedLockService mockLockService;
    
    /**
     * Runs before each test - creates fresh mocks and service instance.
     */
    @BeforeEach
    void setUp() {
        // Create fake repositories that don't touch real database
        mockDeckRepo = mock(DeckRepository.class);
        mockCardRepo = mock(CardRepository.class);
        mockLockService = mock(DistributedLockService.class);
        
        // Create service with fake dependencies
        deckService = new DeckService(mockDeckRepo, mockCardRepo, mockLockService);
    }
    
    @Test
    @DisplayName("User who owns deck should be allowed to modify it")
    void testCanModifyDeck_UserOwnsIt_ShouldReturnTrue() {
        // Arrange: Create fake deck owned by "john"
        Deck deck = new Deck();
        deck.setId(1L);
        deck.setUsername("john");
        
        // Tell mock repository to return this deck when asked for ID 1
        when(mockDeckRepo.findById(1L)).thenReturn(Optional.of(deck));
        
        // Act: Check if john can modify his own deck
        boolean result = deckService.canModifyDeck(1L, "john", false);
        
        // Assert: Should be true (john owns it)
        assertTrue(result, "Owner should be able to modify their own deck");
    }
    
    @Test
    @DisplayName("User who doesn't own deck should NOT be allowed to modify it")
    void testCanModifyDeck_UserDoesntOwnIt_ShouldReturnFalse() {
        // Arrange: Create deck owned by "john"
        Deck deck = new Deck();
        deck.setId(1L);
        deck.setUsername("john");
        
        when(mockDeckRepo.findById(1L)).thenReturn(Optional.of(deck));
        
        // Act: Check if "alice" can modify john's deck
        boolean result = deckService.canModifyDeck(1L, "alice", false);
        
        // Assert: Should be false (alice doesn't own it)
        assertFalse(result, "Non-owner should NOT be able to modify deck");
    }
    
    @Test
    @DisplayName("Admin should be allowed to modify any deck")
    void testCanModifyDeck_AdminUser_ShouldReturnTrue() {
        // Arrange: Create deck owned by "john"
        Deck deck = new Deck();
        deck.setId(1L);
        deck.setUsername("john");
        
        when(mockDeckRepo.findById(1L)).thenReturn(Optional.of(deck));
        
        // Act: Check if "admin" (isAdmin=true) can modify john's deck
        boolean result = deckService.canModifyDeck(1L, "admin", true);
        
        // Assert: Should be true (admin can modify anything)
        assertTrue(result, "Admin should be able to modify any deck");
    }
    
    @Test
    @DisplayName("Non-existent deck should return false")
    void testCanModifyDeck_DeckNotFound_ShouldReturnFalse() {
        // Arrange: Tell mock repository that deck doesn't exist
        when(mockDeckRepo.findById(999L)).thenReturn(Optional.empty());
        
        // Act: Try to check permission for non-existent deck
        boolean result = deckService.canModifyDeck(999L, "john", false);
        
        // Assert: Should be false (deck doesn't exist)
        assertFalse(result, "Should return false for non-existent deck");
    }
    
    @Test
    @DisplayName("Adding card when deck is full should return error")
    void testAddCardToDeck_DeckFull_ShouldReturnError() {
        // Arrange: Create deck with 60 cards (max limit)
        Deck fullDeck = new Deck();
        fullDeck.setId(1L);
        fullDeck.setCards(new ArrayList<>());
        
        // Add 60 dummy cards
        for (int i = 0; i < 60; i++) {
            Card card = new Card();
            card.setName("Card" + i);
            fullDeck.getCards().add(card);
        }
        
        Card newCard = new Card();
        newCard.setName("Blue-Eyes White Dragon");
        
        // Mock responses
        when(mockDeckRepo.findById(1L)).thenReturn(Optional.of(fullDeck));
        when(mockCardRepo.findById("Blue-Eyes White Dragon")).thenReturn(Optional.of(newCard));
        when(mockLockService.acquireLock(anyString(), anyLong())).thenReturn(true);
        
        // Act: Try to add 61st card
        ResponseEnvelope<DeckOperationData> result = deckService.addCardToDeck(1L, "Blue-Eyes White Dragon");
        
        // Assert: Should fail with error message
        assertFalse(result.isSuccess(), "Should not allow adding card to full deck");
        assertTrue(result.getMessage().contains("maximum"), 
            "Error message should mention maximum limit");
    }
    
    @Test
    @DisplayName("Adding 4th copy of same card should return error")
    void testAddCardToDeck_MaxCopiesReached_ShouldReturnError() {
        // Arrange: Create deck with 3 copies of Blue-Eyes already
        Deck deck = new Deck();
        deck.setId(1L);
        deck.setCards(new ArrayList<>());
        
        Card blueEyes = new Card();
        blueEyes.setName("Blue-Eyes White Dragon");
        
        // Add 3 copies (max allowed)
        deck.getCards().add(blueEyes);
        deck.getCards().add(blueEyes);
        deck.getCards().add(blueEyes);
        
        // Mock responses
        when(mockDeckRepo.findById(1L)).thenReturn(Optional.of(deck));
        when(mockCardRepo.findById("Blue-Eyes White Dragon")).thenReturn(Optional.of(blueEyes));
        when(mockLockService.acquireLock(anyString(), anyLong())).thenReturn(true);
        
        // Act: Try to add 4th copy
        ResponseEnvelope<DeckOperationData> result = deckService.addCardToDeck(1L, "Blue-Eyes White Dragon");
        
        // Assert: Should fail with error about max copies
        assertFalse(result.isSuccess(), "Should not allow 4th copy of same card");
        assertTrue(result.getMessage().contains("3 copies"), 
            "Error message should mention 3 copies limit");
    }
    
    @Test
    @DisplayName("Lock not acquired should return error")
    void testAddCardToDeck_LockNotAcquired_ShouldReturnError() {
        // Arrange: Simulate another request holding the lock
        when(mockLockService.acquireLock(anyString(), anyLong())).thenReturn(false);
        
        // Act: Try to add card
        ResponseEnvelope<DeckOperationData> result = deckService.addCardToDeck(1L, "Blue-Eyes White Dragon");
        
        // Assert: Should fail because lock couldn't be acquired
        assertFalse(result.isSuccess(), "Should fail when lock cannot be acquired");
        assertTrue(result.getMessage().contains("being modified") || 
                   result.getMessage().contains("try again"),
            "Error message should indicate resource is locked");
    }
}
