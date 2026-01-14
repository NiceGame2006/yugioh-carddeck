package com.example.yugioh.model;

import com.example.yugioh.util.HtmlSanitizer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Deck entity for managing card collections with Yu-Gi-Oh! rules.
 * 
 * Database Indexes:
 * 1. deck_pkey (PRIMARY KEY on 'id'):
 *    - Auto-created btree index for fast deck lookups by ID
 *    - Used in: getDeck(), updateDeck(), deleteDeck(), addCardToDeck(), removeCardFromDeck()
 *    - Critical for: All deck operations, foreign key references from deck_card table
 * 
 * Many-to-Many Relationship:
 * - deck_card join table has implicit foreign key indexes:
 *   a) Index on deck_id: Fast lookup of all cards in a deck
 *   b) Index on card_name: Fast lookup of all decks containing a card
 * - Used in: findCardsByDeckId(), existsByCardName()
 * 
 * Potential Enhancement:
 * - Consider adding index on 'name' if searching/sorting decks by name becomes common
 */
@Entity
public class Deck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Deck name is required")
    @Size(min = 1, max = 100, message = "Deck name must be between 1 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String username; // Owner of the deck

    @ManyToMany
    @JoinTable(
        name = "deck_card",
        joinColumns = @JoinColumn(name = "deck_id"),
        inverseJoinColumns = @JoinColumn(name = "card_name")
    )
    private List<Card> cards;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public List<Card> getCards() { return cards; }
    public void setCards(List<Card> cards) { this.cards = cards; }
    
    /**
     * Sanitizes user-provided text fields before persisting to database.
     * Prevents XSS attacks by removing HTML/JavaScript from deck names.
     * 
     * Triggered automatically by JPA before INSERT and UPDATE operations.
     */
    @PrePersist
    @PreUpdate
    protected void sanitizeInputs() {
        this.name = HtmlSanitizer.sanitize(this.name);
    }
}
