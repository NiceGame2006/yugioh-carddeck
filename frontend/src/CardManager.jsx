import React, { useEffect, useState } from 'react';
import { fetchCards, createCard, updateCard, deleteCard } from './api';
import useArchetypes from './hooks/useArchetypes';
import useDebounce from './hooks/useDebounce';
import Pagination from './components/Pagination';
import SearchBar from './components/SearchBar';

export default function CardManager({ openCardDetail, showToast, archetypeFilter, clearArchetypeFilter, user, hasRole }) {
  const [cards, setCards] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalCards, setTotalCards] = useState(0);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearchQuery = useDebounce(searchQuery, 300);
  const [form, setForm] = useState({ name: '', humanReadableCardType: '', description: '', race: '', attribute: '', archetypeName: '' });
  const archetypes = useArchetypes();
  const [editing, setEditing] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const resetForm = () => {
    setForm({ name: '', humanReadableCardType: '', description: '', race: '', attribute: '', archetypeName: '' });
  };

  // Convert archetypeName form field to archetype object for API payload
  const prepareCardPayload = (formData) => {
    const payload = { ...formData };
    if (formData.archetypeName && formData.archetypeName.trim() !== '') {
      payload.archetype = { name: formData.archetypeName.trim() };
    } else {
      payload.archetype = null;
    }
    delete payload.archetypeName;
    return payload;
  };

  // Handle archetype filter from ArchetypeManager "View Cards" button
  useEffect(() => {
    if (archetypeFilter) {
      setSearchQuery(archetypeFilter);
      setCurrentPage(0);
      if (clearArchetypeFilter) clearArchetypeFilter();
    }
  }, [archetypeFilter]);

  // Reset to page 0 when search query changes
  useEffect(() => {
    setCurrentPage(0);
  }, [debouncedSearchQuery]);

  useEffect(() => {
    loadCards();
  }, [currentPage, debouncedSearchQuery]);
  
  async function loadCards() {
    setLoading(true);
    try {
      const envelope = await fetchCards(currentPage, 20, debouncedSearchQuery);
      const paginated = envelope.data || {};
      setCards(paginated.items || []);
      setCurrentPage(paginated.currentPage || 0);
      setTotalPages(paginated.totalPages || 0);
      setTotalCards(paginated.totalItems || 0);
    } catch (error) {
      console.error('Error loading cards:', error);
      setCards([]);
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (editing) {
      if (!window.confirm('Save changes?')) return;
      const payload = prepareCardPayload(form);
      try {
        const result = await updateCard(editing, payload);
        if (result && result.success) {
          if (showToast) showToast(result.message || 'Card updated', 'info');
        } else {
          if (showToast) showToast(result.message || 'Failed to update card', 'error');
        }
      } catch (err) {
        console.error(err);
        if (showToast) showToast('Failed to update card: ' + (err.message || ''), 'error');
      }
    } else {
      if (!window.confirm('Create new card?')) return;
      const payload = prepareCardPayload(form);
      try {
        const result = await createCard(payload);
        if (result && result.success) {
          if (showToast) showToast(result.message || 'Card created', 'info');
        } else {
          if (showToast) showToast(result.message || 'Failed to create card', 'error');
        }
      } catch (err) {
        console.error(err);
        if (showToast) showToast('Failed to create card: ' + (err.message || ''), 'error');
      }
    }
    // Clear form and reload list
    resetForm();
    setEditing(null);
    await loadCards();
  }

  function handleEdit(card) {
    // Populate form with card data, flatten archetype.name to archetypeName for easier binding
    setForm({ ...card, archetypeName: card && card.archetype ? card.archetype.name : '' });
    setEditing(card.name);
  }

  async function handleDelete(name) {
    if (!window.confirm('Delete this card?')) return;
    try {
      const result = await deleteCard(name);
      if (result && result.success) {
        // Optimistically update UI without full reload for better UX
        setCards(prev => prev.filter(c => c.name !== name));
        setTotalCards(prev => (typeof prev === 'number' ? Math.max(0, prev - 1) : prev));
        if (showToast) showToast(result.message || 'Card deleted', 'info');
      } else {
        if (showToast) showToast(result.message || 'Failed to delete card', 'error');
      }
    } catch (err) {
      console.error(err);
      if (showToast) showToast('Failed to delete card: ' + (err.message || ''), 'error');
    }
  }

  function goToPage(page) {
    setCurrentPage(page);
  }

  function clearSearch() {
    setSearchQuery('');
    setCurrentPage(0);
  }

  return (
    <div>
      <h2>Cards</h2>
      
      {/* Search/Filter */}
      <SearchBar 
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        onSubmit={(e) => e.preventDefault()}
        onClear={clearSearch}
        placeholder="Search by card name or archetype..."
      />

      {/* Pagination Info */}
      <div style={{ marginBottom: 10 }}>
        {loading ? (
          <p>Loading cards...</p>
        ) : (
          <p>Showing page {currentPage + 1} of {totalPages} (Total: {totalCards} cards)</p>
        )}
      </div>

      {/* Only show form if user has ADMIN role */}
      {hasRole && hasRole('ADMIN') ? (
        <form onSubmit={handleSubmit} style={{ marginBottom: 20 }}>
          <input name="name" value={form.name} onChange={handleChange} placeholder="Name" required disabled={!!editing} maxLength={255} />
          <input name="humanReadableCardType" value={form.humanReadableCardType} onChange={handleChange} placeholder="Type" maxLength={50} />
          <input name="description" value={form.description} onChange={handleChange} placeholder="Description" maxLength={10000} />
          <input name="race" value={form.race} onChange={handleChange} placeholder="Race" maxLength={50} />
          <input name="attribute" value={form.attribute} onChange={handleChange} placeholder="Attribute" maxLength={50} />
          {/* Archetype selector: dropdown to pick or text input for new archetype */}
          <select name="archetypeName" value={form.archetypeName} onChange={handleChange}>
            <option value="">-- Select Archetype or leave blank --</option>
            {archetypes.map(a => (
              <option key={a.id} value={a.name}>{a.name}</option>
            ))}
          </select>
          <input name="archetypeNameCustom" value={form.archetypeName} onChange={(e) => setForm({...form, archetypeName: e.target.value})} placeholder="Or type new archetype" maxLength={100} />
          <button type="submit">{editing ? 'Save' : 'Add'}</button>
          {editing && <button type="button" onClick={() => { setEditing(null); resetForm(); }}>Cancel</button>}
        </form>
      ) : (
        <div style={{ padding: 10, backgroundColor: '#f0f0f0', marginBottom: 20, borderRadius: 4 }}>
          <p style={{ margin: 0 }}>Only administrators can create or edit cards. Please login as admin to manage cards.</p>
        </div>
      )}

      {/* Pagination Controls */}
      <Pagination 
        currentPage={currentPage}
        totalPages={totalPages}
        onPageChange={goToPage}
      />

  <table border="1" cellPadding="4" style={{ width: '100%', tableLayout: 'fixed' }}>
        <thead>
          <tr>
            <th style={{ width: '15%' }}>Name</th>
            <th style={{ width: '10%' }}>Type</th>
            <th style={{ width: '35%' }}>Description</th>
            <th style={{ width: '10%' }}>Race</th>
            <th style={{ width: '10%' }}>Attribute</th>
            <th style={{ width: '10%' }}>Archetype</th>
            <th style={{ width: '10%' }}>Actions</th>
          </tr>
        </thead>
        <tbody>
          {cards.map(card => (
            <tr key={card.name}>
              <td>
                {openCardDetail ? (
                  <button style={{ background: 'none', border: 'none', color: '#007bff', textDecoration: 'underline', cursor: 'pointer', padding: 0 }} onClick={() => openCardDetail(card.name)}>{card.name}</button>
                ) : (
                  card.name
                )}
              </td>
              <td>{card.humanReadableCardType}</td>
              <td style={{ whiteSpace: 'normal', wordWrap: 'break-word' }}>{card.description || '(No description)'}</td>
              <td>{card.race}</td>
              <td>{card.attribute || ''}</td>
              <td>{card.archetype ? card.archetype.name : (card.archetypeName || '')}</td>
              <td>
                {hasRole && hasRole('ADMIN') ? (
                  <>
                    <button onClick={() => handleEdit(card)}>Edit</button>
                    <button onClick={() => handleDelete(card.name)}>Delete</button>
                  </>
                ) : (
                  <span style={{ color: '#999', fontSize: '0.9em' }}>Admin only</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
