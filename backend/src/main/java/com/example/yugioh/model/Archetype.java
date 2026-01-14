package com.example.yugioh.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Archetype entity optimized with minimal necessary indexes.
 * 
 * Database Indexes:
 * 1. archetype_pkey (PRIMARY KEY on 'id'):
 *    - Auto-created btree index for fast archetype lookups by ID
 *    - Used in: Foreign key references from Card.archetype_id
 *    - Critical for: JOIN operations between Card and Archetype tables
 * 
 * 2. uk_sayv73xj0uwyx72e8... (UNIQUE CONSTRAINT on 'name'):
 *    - Auto-created from @Column(unique=true) constraint
 *    - Dual purpose index: enforces uniqueness + provides query performance
 *    - Enforces: No duplicate archetype names in database
 *    - Used in: 
 *      a) searchCards() query - archetype name searches (LOWER(a.name) LIKE)
 *      b) ensureArchetypes() - check existing archetypes before INSERT
 *      c) All WHERE name = ? queries
 *    - Performance: btree index enables O(log n) lookups and text search optimization
 *    - Optimization note: Single index serves both data integrity and performance needs
 */
@Entity
public class Archetype {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Archetype name is required")
    @Size(max = 100, message = "Archetype name must not exceed 100 characters")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(mappedBy = "archetype")
    @JsonIgnore
    private List<Card> cards;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Card> getCards() { return cards; }
    public void setCards(List<Card> cards) { this.cards = cards; }
}
