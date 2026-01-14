import React, { useEffect, useState } from 'react';
import { fetchDecks, createDeck, updateDeck, deleteDeck, fetchCards, addCardToDeck, removeCardFromDeck } from './api';
import useDebounce from './hooks/useDebounce';
import Pagination from './components/Pagination';
import SearchBar from './components/SearchBar';

export default function DeckManager({ showToast, user, hasRole }) {
  const [decks, setDecks] = useState([]);
  const [cards, setCards] = useState([]);
  const [form, setForm] = useState({ name: '' });
  const [editing, setEditing] = useState(null);
  const [selectedDeck, setSelectedDeck] = useState(null);
  const [searchCard, setSearchCard] = useState('');
  const debouncedSearchCard = useDebounce(searchCard, 300);
  const [cardPage, setCardPage] = useState(0);
  const [cardTotalPages, setCardTotalPages] = useState(0);
  const [searchLoading, setSearchLoading] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const resetForm = () => {
    setForm({ name: '' });
  };

  // Backend sometimes returns card names as strings, normalize to objects for consistent rendering
  const normalizeDeck = (deck) => {
    if (!deck) return deck;
    const copy = { ...deck };
    if (!copy.cards) {
      copy.cards = [];
    } else if (Array.isArray(copy.cards) && copy.cards.length > 0 && typeof copy.cards[0] === 'string') {
      copy.cards = copy.cards.map(name => ({ name }));
    }
    return copy;
  };

  // Reset to page 0 when search changes
  useEffect(() => {
    setCardPage(0);
  }, [debouncedSearchCard]);

  useEffect(() => {
    performSearch(cardPage, debouncedSearchCard);
  }, [cardPage, debouncedSearchCard]);

  useEffect(() => {
    loadDecks();
  }, []);
  
  async function loadDecks() {
    const envelope = await fetchDecks();
    const ds = envelope.data || [];
    const normalized = (ds || []).map(d => normalizeDeck(d));
    setDecks(normalized);
  }
  
  async function performSearch(page, query) {
    setSearchLoading(true);
    try {
      const envelope = await fetchCards(page, 20, query);
      const paginated = envelope.data || {};
      setCards(paginated.items || []);
      setCardTotalPages(paginated.totalPages || 0);
    } catch (e) {
      console.error('Search failed', e);
      setCards([]);
    } finally {
      setSearchLoading(false);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (editing) {
      try {
        const result = await updateDeck(editing, form);
        if (result && result.success) {
          if (showToast) showToast(result.message || 'Deck updated', 'info');
        } else {
          if (showToast) showToast(result.message || 'Failed to update deck', 'error');
        }
      } catch (err) {
        console.error(err);
        if (showToast) showToast('Failed to update deck: ' + (err.message || ''), 'error');
      }
    } else {
      try {
        const result = await createDeck(form);
        if (result && result.success) {
          if (showToast) showToast(result.message || 'Deck created', 'info');
          // Navigate to the newly created deck's builder
          const newDeck = normalizeDeck(result.data);
          if (newDeck) {
            resetForm();
            setEditing(null);
            await loadDecks();
            setSelectedDeck(newDeck);
            return;
          }
        } else {
          if (showToast) showToast(result.message || 'Failed to create deck', 'error');
        }
      } catch (err) {
        console.error(err);
        if (showToast) showToast('Failed to create deck: ' + (err.message || ''), 'error');
      }
    }
    resetForm();
    setEditing(null);
    loadDecks();
  }

  function handleEdit(deck) {
    setForm(deck);
    setEditing(deck.id);
  }

  async function handleDelete(id) {
    if (!window.confirm('Are you sure you want to delete this deck?')) return;
    try {
      const result = await deleteDeck(id);
      if (result && result.success) {
        if (showToast) showToast(result.message || 'Deck deleted', 'info');
      } else {
        if (showToast) showToast(result.message || 'Failed to delete deck', 'error');
      }
    } catch (err) {
      console.error(err);
      if (showToast) showToast('Failed to delete deck: ' + (err.message || ''), 'error');
    }
    loadDecks();
    if (selectedDeck && selectedDeck.id === id) {
      setSelectedDeck(null);
    }
  }

  async function handleAddCardToDeck(deckId, cardName) {
    try {
      const result = await addCardToDeck(deckId, cardName);

      if (result.success) {
        // Update UI with server-returned deck data
        if (result.data && result.data.deck) {
          setSelectedDeck(normalizeDeck(result.data.deck));
        }
        const updatedDeck = result.data && result.data.deck ? result.data.deck : {};
        const deckSize = updatedDeck.cards ? updatedDeck.cards.length : 0;
        const copies = result.data ? result.data.copies : 0;
        if (showToast) showToast(`Card "${cardName}" added! Deck: ${deckSize} / 60 cards, ${copies} ${copies === 1 ? 'copy' : 'copies'} of this card.`, 'info');
        // Reload decks list to keep main table in sync
        loadDecks();
      } else {
        if (showToast) showToast(`Error adding card: ${result.message}`, 'error');
      }
    } catch (error) {
      if (showToast) showToast('Error adding card: ' + (error.message || ''), 'error');
    }
  }

  async function handleRemoveCardFromDeck(deckId, cardName) {
    try {
      const result = await removeCardFromDeck(deckId, cardName);

      if (result.success) {
        // Update UI with server-returned deck data
        if (result.data && result.data.deck) {
          setSelectedDeck(normalizeDeck(result.data.deck));
        }
        const updatedDeck = result.data && result.data.deck ? result.data.deck : {};
        const deckSize = updatedDeck.cards ? updatedDeck.cards.length : 0;
        const copies = result.data ? result.data.copies : 0;
        if (showToast) showToast(`Card "${cardName}" removed! Deck: ${deckSize} / 60 cards, ${copies} ${copies === 1 ? 'copy' : 'copies'} remaining.`, 'info');
        // Reload decks list to keep main table in sync
        loadDecks();
      } else {
        if (showToast) showToast(`Error removing card: ${result.message}`, 'error');
      }
    } catch (error) {
      if (showToast) showToast('Error removing card: ' + (error.message || ''), 'error');
    }
  }

  function viewDeck(deck) {
    setSelectedDeck(normalizeDeck(deck));
  }

  return (
    <div>
      <h2>Decks</h2>
      
      {/* Only show form if user has USER or ADMIN role */}
      {hasRole && (hasRole('USER') || hasRole('ADMIN')) ? (
        <form onSubmit={handleSubmit} style={{ marginBottom: 20 }}>
          <input name="name" value={form.name} onChange={handleChange} placeholder="Name" required maxLength={100} />
          <button type="submit">{editing ? 'Update' : 'Add'}</button>
          {editing && <button type="button" onClick={() => { setEditing(null); resetForm(); }}>Cancel</button>}
        </form>
      ) : (
        <div style={{ padding: 10, backgroundColor: '#f0f0f0', marginBottom: 20, borderRadius: 4 }}>
          <p style={{ margin: 0 }}>Only registered users can create or edit decks. Please login to manage decks.</p>
        </div>
      )}
      
      <table border="1" cellPadding="4">
        <thead>
          <tr>
            <th>ID</th><th>Name</th><th>Owner</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {decks.map(d => (
            <tr key={d.id}>
              <td>{d.id}</td>
              <td>{d.name}</td>
              <td>{d.username || 'Unknown'}</td>
              <td>
                {(() => {
                  const isOwner = user && user.username === d.username;
                  const isAdmin = hasRole && hasRole('ADMIN');
                  const canModify = isOwner || isAdmin;
                  
                  return (
                    <>
                      {canModify ? (
                        <>
                          <button onClick={() => handleEdit(d)}>Edit</button>
                          <button onClick={() => handleDelete(d.id)}>Delete</button>
                          <button onClick={() => viewDeck(d)}>Build</button>
                        </>
                      ) : (
                        <>
                          <button onClick={() => viewDeck(d)}>View</button>
                          {!user && <span style={{ color: '#999', fontSize: '0.9em', marginLeft: 5 }}>Login to edit</span>}
                        </>
                      )}
                    </>
                  );
                })()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Deck Builder Section */}
      {selectedDeck && (
        <div style={{ marginTop: '30px', padding: '20px', border: '2px solid #ccc', borderRadius: '8px', minWidth: '1200px' }}>
          {(() => {
            const deckSize = selectedDeck && selectedDeck.cards ? selectedDeck.cards.length : 0;
            const remaining = Math.max(0, 60 - deckSize);
            return (
              <h3>Deck Builder: {selectedDeck.name} — {deckSize} / 60 cards ({remaining} slots left)</h3>
            );
          })()}
          
          {/* Current Deck Cards */}
          <div style={{ marginBottom: '20px' }}>
            <h4>Cards in Deck ({selectedDeck.cards ? selectedDeck.cards.length : 0}):</h4>
                {selectedDeck.cards && selectedDeck.cards.length > 0 ? (
              <div style={{ maxHeight: '200px', overflowY: 'auto', border: '1px solid #ddd', padding: '10px' }}>
                {selectedDeck.cards.map((card, index) => {
                  const isOwner = user && user.username === selectedDeck.username;
                  const isAdmin = hasRole && hasRole('ADMIN');
                  const canModify = isOwner || isAdmin;
                  
                  return (
                    <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '5px', borderBottom: '1px solid #eee' }}>
                      <span>{card.name} - {(card.type || card.humanReadableCardType || '')}</span>
                      {canModify ? (
                        <button 
                          onClick={() => handleRemoveCardFromDeck(selectedDeck.id, card.name)}
                          style={{ backgroundColor: '#ff4444', color: 'white', border: 'none', padding: '2px 8px', borderRadius: '4px', cursor: 'pointer' }}
                        >
                          Remove
                        </button>
                      ) : (
                        <span style={{ color: '#999', fontSize: '0.8em' }}>View only</span>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <p>No cards in this deck yet.</p>
            )}
          </div>

          {/* Card Search and Add - only for deck owner or admin */}
          {(() => {
            const isOwner = user && user.username === selectedDeck.username;
            const isAdmin = hasRole && hasRole('ADMIN');
            const canModify = isOwner || isAdmin;
            
            return canModify ? (
            <div>
              <h4>Add Cards to Deck:</h4>
              <SearchBar
                value={searchCard}
                onChange={(e) => setSearchCard(e.target.value)}
                onSubmit={(e) => e.preventDefault()}
                onClear={() => setSearchCard('')}
                placeholder="Search cards..."
              />
              
              <div style={{ maxHeight: '300px', overflowY: 'auto', border: '1px solid #ddd', padding: '10px' }}>
                {searchLoading ? (
                  <p>Searching...</p>
                ) : cards.length > 0 ? (
                  cards.map((card) => {
                    const copiesInDeck = selectedDeck && selectedDeck.cards ? selectedDeck.cards.filter(c => c.name === card.name).length : 0;
                    const deckFull = selectedDeck && selectedDeck.cards ? selectedDeck.cards.length >= 60 : false;
                    const reachedCopiesLimit = copiesInDeck >= 3;
                    return (
                      <div key={card.name} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px', borderBottom: '1px solid #eee' }}>
                        <div>
                          <strong>{card.name}</strong> - {(card.type || card.humanReadableCardType || '')}
                          <br />
                          <small>{(card.desc || card.description) ? ( (card.desc || card.description).substring(0, 100) + '...' ) : 'No description'}</small>
                        </div>
                        {/* Yu-Gi-Oh! deck building rules: max 60 cards total, max 3 copies per card */}
                        <button 
                          onClick={() => {
                            if (deckFull) {
                              if (showToast) showToast('Deck already has 60 cards', 'error');
                              return;
                            }
                            if (reachedCopiesLimit) {
                              if (showToast) showToast('Already 3 copies of this card in deck', 'error');
                              return;
                            }
                            handleAddCardToDeck(selectedDeck.id, card.name);
                          }}
                          disabled={reachedCopiesLimit || deckFull}
                          style={{ backgroundColor: (reachedCopiesLimit || deckFull) ? '#999' : '#4CAF50', color: 'white', border: 'none', padding: '5px 10px', borderRadius: '4px', cursor: (reachedCopiesLimit || deckFull) ? 'not-allowed' : 'pointer' }}
                        >
                          {reachedCopiesLimit ? `Added (${copiesInDeck})` : (deckFull ? 'Deck full' : 'Add')}
                        </button>
                      </div>
                    );
                  })
                ) : (
                  <p>No cards found. Try searching for card names.</p>
                )}
              </div>

              {/* Pagination for search results */}
              <div style={{ marginTop: 15 }}>
                <Pagination 
                  currentPage={cardPage}
                  totalPages={cardTotalPages}
                  onPageChange={(page) => setCardPage(page)}
                />
              </div>
            </div>
          ) : (
            <div style={{ padding: 10, backgroundColor: '#f0f0f0', marginTop: 20, borderRadius: 4 }}>
              <p style={{ margin: 0 }}>Only the deck owner or admin can modify this deck.</p>
            </div>
          );
          })()}
            
          <button 
            onClick={() => setSelectedDeck(null)}
            style={{ marginTop: '15px', padding: '8px 16px', backgroundColor: '#666', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
          >
            Close Deck Builder
          </button>
        </div>
      )}
    </div>
  );
}
