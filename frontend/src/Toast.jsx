import React, { useEffect } from 'react';

export default function Toast({ message, type = 'info', onClose, duration = 3000 }) {
  // Auto-dismiss toast after duration, cleanup timer on unmount or message change
  useEffect(() => {
    if (!message) return;
    const t = setTimeout(() => onClose && onClose(), duration);
    return () => clearTimeout(t);
  }, [message, duration, onClose]);

  if (!message) return null;

  const bg = type === 'error' ? '#f8d7da' : '#d1e7dd';
  const color = type === 'error' ? '#842029' : '#0f5132';

  return (
    <div style={{ position: 'fixed', right: 20, top: 20, background: bg, color, padding: '10px 14px', borderRadius: 6, boxShadow: '0 2px 6px rgba(0,0,0,0.15)', zIndex: 1000 }}>
      {message}
    </div>
  );
}
