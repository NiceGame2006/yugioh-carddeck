package com.example.yugioh.dto;

import com.example.yugioh.model.Deck;

/**
 * Data transfer object for deck card operations (add/remove card responses).
 * 
 * Purpose:
 * - Wraps the updated Deck entity with additional metadata
 * - Allows frontend to display rich feedback without additional API calls
 * - Used inside ResponseEnvelope<DeckOperationData> for consistent API responses
 * 
 * Example Response:
 * {
 *   "success": true,
 *   "message": "Card added successfully",
 *   "data": {
 *     "deck": { "id": 1, "name": "Blue-Eyes Deck", "cards": [...] },
 *     "deckSize": 45,
 *     "copies": 2
 *   }
 * }
 * 
 * Frontend Usage:
 * - deck: Re-render deck builder with updated card list
 * - deckSize: Display "45/60 cards" progress indicator
 * - copies: Show "2/3 copies of this card" validation feedback
 * 
 * Used by:
 * - DeckService.addCardToDeck() and removeCardFromDeck()
 * - DeckController add/remove endpoints
 */
public class DeckOperationData {
    private Deck deck;
    private Integer deckSize;
    private Integer copies;

    public DeckOperationData() {}

    public DeckOperationData(Deck deck, Integer deckSize, Integer copies) {
        this.deck = deck;
        this.deckSize = deckSize;
        this.copies = copies;
    }

    public Deck getDeck() { return deck; }
    public void setDeck(Deck deck) { this.deck = deck; }

    public Integer getDeckSize() { return deckSize; }
    public void setDeckSize(Integer deckSize) { this.deckSize = deckSize; }

    public Integer getCopies() { return copies; }
    public void setCopies(Integer copies) { this.copies = copies; }
}
