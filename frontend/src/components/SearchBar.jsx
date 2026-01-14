import React from 'react';

export default function SearchBar({ value, onChange, onSubmit, onClear, placeholder = "Search..." }) {
  return (
    <div style={{ marginBottom: 15, padding: 10, backgroundColor: '#f0f0f0', borderRadius: 4 }}>
      <form onSubmit={onSubmit} style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
        <input 
          type="text" 
          value={value} 
          onChange={onChange}
          placeholder={placeholder} 
          style={{ flex: 1, padding: 5 }}
        />
        <button type="submit">Search</button>
        {/* Only show Clear button when there's a value and onClear handler is provided */}
        {value && onClear && <button type="button" onClick={onClear}>Clear</button>}
      </form>
      {value && (
        <p style={{ margin: '5px 0 0 0', fontSize: '0.9em', color: '#666' }}>
          Filtering by: <strong>{value}</strong>
        </p>
      )}
    </div>
  );
}
