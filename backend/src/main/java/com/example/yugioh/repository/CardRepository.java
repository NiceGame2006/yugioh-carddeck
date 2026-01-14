package com.example.yugioh.repository;

import com.example.yugioh.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Collection;

public interface CardRepository extends JpaRepository<Card, String> {

    // ========================================
    // FREQUENTLY USED METHODS (Active in production)
    // ========================================

    /**
     * Searches cards by name or archetype name with pagination and case-insensitive matching.
     * Results are ordered alphabetically (case-insensitive) by card name.
     * 
     * Used in: CardCacheService.searchCards()
     * 
     * @param query Search term to match against card name or archetype name
     * @param pageable Pagination parameters (page number, size, sort)
     * @return Page of matching cards with pagination metadata
     */
    @Query("SELECT c FROM Card c LEFT JOIN c.archetype a " +
           "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY FUNCTION('UPPER', c.name)")
    Page<Card> searchCards(@Param("query") String query, Pageable pageable);

    /**
     * Retrieves all cards with custom collation sorting for consistent alphabetical ordering.
     * Uses native SQL with UPPER() and COLLATE "C" for case-insensitive, deterministic sorting.
     * 
     * Used in: CardCacheService.getAllCards()
     * 
     * @param pageable Pagination parameters (page number, size)
     * @return Page of all cards with pagination metadata
     */
    @Query(value = "SELECT c.* FROM card c LEFT JOIN archetype a ON c.archetype_id = a.id " +
           "ORDER BY UPPER(c.name) COLLATE \"C\"",
           countQuery = "SELECT count(*) FROM card",
           nativeQuery = true)
    Page<Card> findAllWithCustomSort(Pageable pageable);

    /**
     * Counts cards belonging to a specific archetype.
     * Used to check if archetype still has associated cards before deletion.
     * 
     * Used in: CardCacheService.deleteCard() (cascade check)
     * 
     * @param archetypeName The archetype name to count cards for
     * @return Number of cards in the archetype
     */
    long countByArchetypeName(String archetypeName);

    // ========================================
    // FUTURE USE METHODS (Available for upcoming features)
    // ========================================

    // ========== BASIC FIELD QUERIES ==========
    
    List<Card> findByHumanReadableCardType(String cardType);
    List<Card> findByRace(String race);
    List<Card> findByAttribute(String attribute);
    List<Card> findByArchetypeName(String archetypeName);
    List<Card> findByDecksName(String deckName);

    // ========== COMBINED FIELD QUERIES ==========
    
    List<Card> findByHumanReadableCardTypeAndRace(String cardType, String race);
    List<Card> findByRaceOrAttribute(String race, String attribute);
    List<Card> findByRaceAndAttributeOrderByNameAsc(String race, String attribute);
    List<Card> findByDescriptionContainingAndRaceNot(String text, String excludeRace);

    // ========== TEXT SEARCH METHODS ==========
    
    List<Card> findByDescriptionContaining(String text);
    List<Card> findByNameStartingWith(String prefix);
    List<Card> findByNameContainingIgnoreCase(String text);
    List<Card> findByNameEndingWith(String suffix);
    List<Card> findByNameLike(String pattern);
    List<Card> findByNameNotContaining(String text);

    // ========== NULL/EXISTENCE CHECKS ==========
    
    List<Card> findByArchetypeIsNull();
    List<Card> findByArchetypeIsNotNull();
    boolean existsByArchetypeName(String archetypeName);

    // ========== NEGATION/EXCLUSION QUERIES ==========
    
    List<Card> findByRaceNot(String race);

    // ========== COLLECTION QUERIES ==========
    
    List<Card> findByRaceIn(Collection<String> races);
    List<Card> findByAttributeIn(Collection<String> attributes);

    // ========== SORTED QUERIES ==========
    
    List<Card> findByRaceOrderByNameAsc(String race);
    List<Card> findByRaceOrderByNameDesc(String race);
    List<Card> findByHumanReadableCardTypeOrderByNameAscRaceDesc(String type);
    Card findFirstByRaceOrderByName(String race);

    // ========== LIMITED RESULT QUERIES ==========
    
    List<Card> findTop10ByRace(String race);
    List<Card> findFirst5ByHumanReadableCardType(String type);
    List<Card> findDistinctByRace(String race);

    // ========== COUNTING METHODS ==========
    
    long countByHumanReadableCardType(String cardType);
}
