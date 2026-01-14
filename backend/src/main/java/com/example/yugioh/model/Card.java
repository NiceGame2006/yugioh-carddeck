package com.example.yugioh.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * Card entity with optimized database indexes for search performance.
 * 
 * Database Indexes:
 * 1. card_pkey (PRIMARY KEY on 'name'): 
 *    - Auto-created btree index for fast card lookups by name
 *    - Used in: fetchCard(), updateCard(), deleteCard(), deck operations
 *    - Critical for: Single card retrieval, foreign key references from deck_card table
 * 
 * 2. idx_card_archetype (on 'archetype_id'):
 *    - Speeds up joins between Card and Archetype tables
 *    - Used in: searchCards() LEFT JOIN, findAllWithCustomSort() JOIN
 *    - Enables: Fast filtering by archetype (findByArchetypeName, countByArchetypeName)
 *    - Performance impact: Makes archetype-based searches O(log n) instead of O(n)
 * 
 * 3. idx_card_type (on 'humanReadableCardType'):
 *    - Speeds up filtering cards by type (Monster, Spell, Trap)
 *    - Used in: findByHumanReadableCardType(), countByHumanReadableCardType()
 *    - Potential use: Type-based statistics, filtered card lists
 *    - Note: Currently defined but not heavily used in active queries
 */
@Entity
@Table(indexes = {
    @Index(name = "idx_card_archetype", columnList = "archetype_id"),
    @Index(name = "idx_card_type", columnList = "humanReadableCardType")
})
public class Card {
    @Id
    @NotBlank(message = "Card name is required")
    @Size(max = 255, message = "Card name must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String name;
    
    @Size(max = 100, message = "Card type must not exceed 100 characters")
    @Column(length = 100)
    private String humanReadableCardType;
    
    @Size(max = 10000, message = "Description must not exceed 10000 characters")
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Size(max = 50, message = "Race must not exceed 50 characters")
    @Column(length = 50)
    private String race;
    
    @Size(max = 50, message = "Attribute must not exceed 50 characters")
    @Column(length = 50)
    private String attribute;

    @ManyToOne
    @JoinColumn(name = "archetype_id")
    private Archetype archetype;

    @ManyToMany(mappedBy = "cards")
    @JsonIgnore
    private List<Deck> decks;

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHumanReadableCardType() { return humanReadableCardType; }
    public void setHumanReadableCardType(String humanReadableCardType) { this.humanReadableCardType = humanReadableCardType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRace() { return race; }
    public void setRace(String race) { this.race = race; }
    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }
    public Archetype getArchetype() { return archetype; }
    public void setArchetype(Archetype archetype) { this.archetype = archetype; }
    public List<Deck> getDecks() { return decks; }
    public void setDecks(List<Deck> decks) { this.decks = decks; }
}
