package com.example.yugioh.repository;

import com.example.yugioh.model.Card;
import com.example.yugioh.model.Deck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeckRepository extends JpaRepository<Deck, Long> {

    @Query("SELECT c FROM Deck d JOIN d.cards c WHERE d.id = :deckId")
    List<Card> findCardsByDeckId(@Param("deckId") Long deckId);

    // Check if a card is used in any deck
    @Query("SELECT COUNT(d) > 0 FROM Deck d JOIN d.cards c WHERE c.name = :cardName")
    boolean existsByCardName(@Param("cardName") String cardName);

    // Find all decks owned by a specific user
    List<Deck> findByUsername(String username);

}
