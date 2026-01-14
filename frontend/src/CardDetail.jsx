import React, { useEffect, useState } from 'react';
import { fetchCard, updateCard, deleteCard } from './api';
import useArchetypes from './hooks/useArchetypes';

export default function CardDetail({ name, onBack, showToast, hasRole }) {
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(null);
  const archetypes = useArchetypes();

  useEffect(() => {
    let mounted = true;
    async function loadCard() {
      setLoading(true);
      try {
        const envelope = await fetchCard(name);
        const data = envelope.data;
        if (mounted && data) {
          setForm({
            name: data.name,
            humanReadableCardType: data.humanReadableCardType || '',
            description: data.description || '',
            race: data.race || '',
            attribute: data.attribute || '',
            archetypeName: data.archetype ? data.archetype.name : ''
          });
        }
      } catch (e) {
        console.error('Failed to load card', e);
      } finally {
        setLoading(false);
      }
    }
    loadCard();
    return () => { mounted = false; };
  }, [name]);

  function handleChange(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

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

  async function handleSave(e) {
    e.preventDefault();
    if (!window.confirm('Save changes?')) return;
    const payload = prepareCardPayload(form);
    try {
      const result = await updateCard(name, payload);
      if (result && result.success) {
        const updated = result.data;
        if (showToast) showToast(result.message || 'Card saved', 'info');
        // Sync form with server-returned data (ensures consistency with any server-side transformations)
        setForm({
          name: updated.name,
          humanReadableCardType: updated.humanReadableCardType || '',
          description: updated.description || '',
          race: updated.race || '',
          attribute: updated.attribute || '',
          archetypeName: updated.archetype ? updated.archetype.name : ''
        });
      } else {
        if (showToast) showToast(result.message || 'Failed to save card', 'error');
      }
    } catch (e) {
      console.error('Failed to save card', e);
      if (showToast) showToast('Failed to save card: ' + (e.message || ''), 'error');
    }
  }

  async function handleDelete() {
    if (!window.confirm('Delete this card?')) return;
    try {
      const result = await deleteCard(name);
      if (result && result.success) {
        if (showToast) showToast(result.message || 'Card deleted', 'info');
        onBack();
      } else {
        if (showToast) showToast(result.message || 'Failed to delete card', 'error');
      }
    } catch (e) {
      console.error('Failed to delete card', e);
      if (showToast) showToast('Failed to delete card: ' + (e.message || ''), 'error');
    }
  }

  if (loading || !form) return <div>Loading card...</div>;

  const isAdmin = hasRole && hasRole('ADMIN');

  return (
    <div>
      <button onClick={onBack}>Back to list</button>
      <h2>Card: {name}</h2>
      
      {!isAdmin && (
        <div style={{ padding: 10, backgroundColor: '#f0f0f0', marginBottom: 20, borderRadius: 4 }}>
          <p style={{ margin: 0 }}>Only administrators can edit or delete cards. Login as admin to make changes.</p>
        </div>
      )}

      <form onSubmit={isAdmin ? handleSave : (e) => e.preventDefault()} style={{ marginBottom: 20 }}>
        <div>
          <label>Name</label>
          <input name="name" value={form.name} disabled />
        </div>
        <div>
          <label>Type</label>
          <input 
            name="humanReadableCardType" 
            value={form.humanReadableCardType} 
            onChange={isAdmin ? handleChange : undefined} 
            disabled={!isAdmin}
            maxLength={50} 
          />
        </div>
        <div>
          <label>Description</label>
          <textarea 
            name="description" 
            value={form.description} 
            onChange={isAdmin ? handleChange : undefined} 
            disabled={!isAdmin}
            rows={8} 
            style={{ width: '100%', maxWidth: '600px' }} 
            maxLength={10000} 
          />
        </div>
        <div>
          <label>Race</label>
          <input 
            name="race" 
            value={form.race} 
            onChange={isAdmin ? handleChange : undefined} 
            disabled={!isAdmin}
            maxLength={50} 
          />
        </div>
        <div>
          <label>Attribute</label>
          <input 
            name="attribute" 
            value={form.attribute} 
            onChange={isAdmin ? handleChange : undefined} 
            disabled={!isAdmin}
            maxLength={50} 
          />
        </div>
        <div>
          <label>Archetype</label>
          {isAdmin ? (
            <>
              <select name="archetypeName" value={form.archetypeName} onChange={handleChange}>
                <option value="">-- Select Archetype or leave blank --</option>
                {archetypes.map(a => (
                  <option key={a.id} value={a.name}>{a.name}</option>
                ))}
              </select>
              <input 
                name="archetypeNameCustom" 
                value={form.archetypeName} 
                onChange={(e) => setForm({...form, archetypeName: e.target.value})} 
                placeholder="Or type new archetype" 
                maxLength={100} 
              />
            </>
          ) : (
            <input value={form.archetypeName || 'None'} disabled />
          )}
        </div>
        {isAdmin && (
          <div>
            <button type="submit">Save</button>
            <button type="button" onClick={handleDelete} style={{ marginLeft: 10 }}>Delete</button>
          </div>
        )}
      </form>
    </div>
  );
}
