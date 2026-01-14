import React, { useState, useEffect } from 'react';

export default function Pagination({ currentPage, totalPages, onPageChange }) {
  const [jumpPageInput, setJumpPageInput] = useState('');

  // Keep jump input synced with current page
  useEffect(() => {
    setJumpPageInput((currentPage + 1).toString());
  }, [currentPage]);
  return (
    <div style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap' }}>
        <button onClick={() => onPageChange(0)} disabled={currentPage === 0}>&laquo; First</button>
        <button onClick={() => onPageChange(currentPage - 1)} disabled={currentPage === 0}>Previous</button>
      </div>

      <div style={{ flex: 1, display: 'flex', justifyContent: 'center', minWidth: 0 }}>
        {/* Page number buttons with ellipsis for large page counts */}
        {totalPages > 0 && (() => {
          const buttons = [];
          const maxVisible = 5; // Show at most 5 page numbers at once
          const half = Math.floor(maxVisible / 2);
          let start = Math.max(0, currentPage - half);
          let end = Math.min(totalPages - 1, currentPage + half);
          
          // Adjust range to always show maxVisible buttons when possible
          if (currentPage - start < half) {
            end = Math.min(totalPages - 1, end + (half - (currentPage - start)));
          }
          if (end - currentPage < half) {
            start = Math.max(0, start - (half - (end - currentPage)));
          }

          // Show first page and ellipsis if we're not starting from beginning
          if (start > 0) {
            buttons.push(<button key="p0" onClick={() => onPageChange(0)} style={{ margin: '0 4px' }}>1</button>);
            if (start > 1) buttons.push(<span key="lsep" style={{ margin: '0 4px' }}>...</span>);
          }

          for (let p = start; p <= end; p++) {
            buttons.push(
              <button 
                key={p} 
                onClick={() => onPageChange(p)} 
                style={{ 
                  margin: '0 4px', 
                  backgroundColor: p === currentPage ? '#007bff' : '#f8f9fa', 
                  color: p === currentPage ? 'white' : 'black' 
                }}
              >
                {p + 1}
              </button>
            );
          }

          if (end < totalPages - 1) {
            if (end < totalPages - 2) buttons.push(<span key="rsep" style={{ margin: '0 4px' }}>...</span>);
            buttons.push(
              <button key="plast" onClick={() => onPageChange(totalPages - 1)} style={{ margin: '0 4px' }}>
                {totalPages}
              </button>
            );
          }

          return buttons;
        })()}
      </div>

      <div style={{ display: 'flex', gap: 4, whiteSpace: 'nowrap' }}>
        <button onClick={() => onPageChange(currentPage + 1)} disabled={currentPage >= totalPages - 1}>Next</button>
        <button onClick={() => onPageChange(totalPages - 1)} disabled={currentPage >= totalPages - 1}>Last &raquo;</button>
      </div>

      <div style={{ marginLeft: 8, whiteSpace: 'nowrap' }}>
        <span>Page {currentPage + 1} of {totalPages}</span>
      </div>

      {/* Jump to specific page input (converts 1-based display to 0-based internal pages) */}
      <div style={{ display: 'flex', gap: 4, marginLeft: 8, whiteSpace: 'nowrap' }}>
        <input 
          type="number" 
          min={1} 
          max={Math.max(1, totalPages)} 
          value={jumpPageInput} 
          onChange={(e) => setJumpPageInput(e.target.value)} 
          placeholder="Go to page" 
          style={{ width: 90 }} 
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              const n = parseInt(jumpPageInput, 10);
              if (!isNaN(n)) {
                // Convert 1-based user input to 0-based page index, clamped to valid range
                const target = Math.max(0, Math.min(totalPages - 1, n - 1));
                onPageChange(target);
              }
            }
          }}
        />
        <button onClick={() => {
          const n = parseInt(jumpPageInput, 10);
          if (!isNaN(n)) {
            const target = Math.max(0, Math.min(totalPages - 1, n - 1));
            onPageChange(target);
          }
        }}>
          Go
        </button>
      </div>
    </div>
  );
}
