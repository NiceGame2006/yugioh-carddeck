package com.example.yugioh.service;

import com.example.yugioh.model.Deck;
import com.example.yugioh.model.Card;
import com.example.yugioh.repository.DeckRepository;
import com.example.yugioh.repository.CardRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.yugioh.dto.DeckOperationData;
import com.example.yugioh.dto.ResponseEnvelope;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

// Manages deck operations - adding/removing cards with Yu-Gi-Oh rules (max 60 cards, max 3 copies)
@Service
public class DeckService {

    private static final int MAX_DECK_SIZE = 60;
    private static final int MAX_COPIES_PER_CARD = 3;

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final DistributedLockService lockService;

    public DeckService(DeckRepository deckRepository, CardRepository cardRepository,
                       DistributedLockService lockService) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.lockService = lockService;
    }

    // Check if user owns the deck (or is admin)
    public boolean canModifyDeck(Long deckId, String username, boolean isAdmin) {
        if (isAdmin) {
            return true; // Admins can modify any deck
        }
        
        Optional<Deck> deckOpt = deckRepository.findById(deckId);
        if (deckOpt.isEmpty()) {
            return false;
        }
        
        Deck deck = deckOpt.get();
        return username != null && username.equals(deck.getUsername());
    }

    @Transactional
    public ResponseEnvelope<DeckOperationData> addCardToDeck(Long deckId, String cardName) {
        String lockKey = "deck:" + deckId;
        
        // Acquire distributed lock to prevent concurrent modifications
        // Prevents: Adding same card twice simultaneously, violating max 3 copies rule
        if (!lockService.acquireLock(lockKey, 5)) {
            return ResponseEnvelope.failed(
                "Deck is being modified by another request. Please try again.");
        }
        
        try {
            Optional<Deck> deckOpt = deckRepository.findById(deckId);
            Optional<Card> cardOpt = cardRepository.findById(cardName);

            if (deckOpt.isEmpty()) {
                return ResponseEnvelope.failed("Deck not found");
            }

            if (cardOpt.isEmpty()) {
                return ResponseEnvelope.failed("Card not found");
            }

            Deck deck = deckOpt.get();

            if (deck.getCards() == null) {
                deck.setCards(new ArrayList<>());
            }

            int currentDeckSize = deck.getCards().size();
            if (currentDeckSize >= MAX_DECK_SIZE) {
                return ResponseEnvelope.failed("Deck already has maximum allowed " + MAX_DECK_SIZE + " cards");
            }

            long copies = deck.getCards().stream()
                .filter(c -> c != null && cardName.equals(c.getName()))
                .count();
                
            if (copies >= MAX_COPIES_PER_CARD) {
                return ResponseEnvelope.failed("Deck already contains " + MAX_COPIES_PER_CARD + " copies of this card");
            }

            deck.getCards().add(cardOpt.get());
            deckRepository.save(deck);

            List<Card> cards = deckRepository.findCardsByDeckId(deckId);
            Deck savedDeck = deckRepository.findById(deckId).orElse(deck);

            DeckOperationData data = new DeckOperationData(savedDeck, cards.size(), (int) copies + 1);
            return ResponseEnvelope.success("Card added to deck", data);
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    @Transactional
    public ResponseEnvelope<DeckOperationData> removeCardFromDeck(Long deckId, String cardName) {
        String lockKey = "deck:" + deckId;
        
        // Acquire distributed lock for consistency with add operation
        if (!lockService.acquireLock(lockKey, 5)) {
            return ResponseEnvelope.failed(
                "Deck is being modified by another request. Please try again.");
        }
        
        try {
            Optional<Deck> deckOpt = deckRepository.findById(deckId);
            Optional<Card> cardOpt = cardRepository.findById(cardName);

            if (deckOpt.isEmpty() || cardOpt.isEmpty()) {
                return ResponseEnvelope.failed("Deck or card not found");
            }

            Deck deck = deckOpt.get();

            if (deck.getCards() != null) {
                boolean removed = false;
                for (int i = 0; i < deck.getCards().size(); i++) {
                    Card c = deck.getCards().get(i);
                    if (c != null && cardName.equals(c.getName())) {
                        deck.getCards().remove(i);
                        removed = true;
                        break;
                    }
                }
                if (removed) {
                    deckRepository.save(deck);
                }
            }

            List<Card> cards = deckRepository.findCardsByDeckId(deckId);
            Deck savedDeck = deckRepository.findById(deckId).orElse(deck);

            long remainingCopies = cards.stream()
                .filter(c -> c != null && cardName.equals(c.getName()))
                .count();
                
            DeckOperationData data = new DeckOperationData(savedDeck, cards.size(), (int) remainingCopies);
            return ResponseEnvelope.success("Card removed from deck", data);
        } finally {
            lockService.releaseLock(lockKey);
        }
    }
}