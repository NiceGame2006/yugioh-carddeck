import { useState, useEffect } from 'react';
import { fetchArchetypes } from '../api';

// Loads archetypes once per mount to avoid redundant API calls across CardManager and CardDetail
export default function useArchetypes() {
  const [archetypes, setArchetypes] = useState([]);

  useEffect(() => {
    let mounted = true;
    async function load() {
      try {
        const envelope = await fetchArchetypes();
        if (!mounted) return; // Prevent state update if component unmounted
        const data = envelope.data || [];
        setArchetypes(Array.isArray(data) ? data : []);
      } catch (e) {
        if (!mounted) return;
        console.warn('Failed to load archetypes', e);
        setArchetypes([]);
      }
    }
    load();
    return () => { mounted = false; }; // Cleanup to prevent memory leaks
  }, []);

  return archetypes;
}
