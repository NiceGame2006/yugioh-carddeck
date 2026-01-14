import React, { useEffect, useState } from 'react';
import { fetchArchetypes } from './api';

export default function ArchetypeManager({ viewArchetypeCards }) {
  const [archetypes, setArchetypes] = useState([]);
  
  useEffect(() => {
    loadArchetypes();
  }, []);
  async function loadArchetypes() { 
    const envelope = await fetchArchetypes();
    const data = envelope.data || [];
    setArchetypes(data || []);
  }

  return (
    <div>
      <h2>Archetypes</h2>
      <div style={{ marginBottom: 20 }}></div>
      <table border="1" cellPadding="4">
        <thead>
          <tr>
            <th>ID</th><th>Name</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {archetypes.map(a => (
            <tr key={a.id}>
              <td>{a.id}</td>
              <td>{a.name}</td>
              <td>
                <button onClick={() => viewArchetypeCards(a.name)}>View Cards</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
