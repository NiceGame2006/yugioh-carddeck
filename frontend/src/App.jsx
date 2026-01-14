
import React, { useState, useEffect } from 'react';
import CardManager from './CardManager';
import ArchetypeManager from './ArchetypeManager';
import DeckManager from './DeckManager';
import CardDetail from './CardDetail';
import Login from './Login';
import UsersManager from './UsersManager';
import Toast from './Toast';
import { getCurrentUser, logout } from './api';

function App() {
  // page can be: 'login', 'cards', 'archetypes', 'decks', 'users', 'cardDetail'
  const [page, setPage] = useState('cards');
  const [selectedCard, setSelectedCard] = useState(null);
  const [archetypeFilter, setArchetypeFilter] = useState(null);
  const [toast, setToast] = useState({ message: '', type: 'info' });
  const [user, setUser] = useState(null); // { username, roles: [], authenticated }

  // Check if user is already logged in on mount
  useEffect(() => {
    checkAuth();
  }, []);

  async function checkAuth() {
    try {
      const accessToken = sessionStorage.getItem('accessToken');
      if (!accessToken) return;

      const result = await getCurrentUser();
      
      if (result.success && result.data) {
        setUser(result.data);
      }
    } catch (error) {
      console.error('Auth check failed:', error);
    }
  }

  function showToast(message, type = 'info', duration = 3000) {
    setToast({ message, type });
    setTimeout(() => setToast({ message: '', type: 'info' }), duration + 100);
  }

  function openCardDetail(name) {
    setSelectedCard(name);
    setPage('cardDetail');
  }

  function backToList() {
    setSelectedCard(null);
    setPage('cards');
  }

  function viewArchetypeCards(archetypeName) {
    setArchetypeFilter(archetypeName);
    setPage('cards');
  }

  function handleLoginSuccess(userData) {
    setUser(userData);
    setPage('cards');
  }

  async function handleLogout() {
    // Revoke refresh token on server (access token will expire naturally)
    try {
      const refreshToken = sessionStorage.getItem('refreshToken');
      if (refreshToken) {
        await logout(refreshToken);
      }
    } catch (error) {
      console.error('Logout error:', error);
    }
    
    // Clear all tokens from client
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
    sessionStorage.removeItem('username');
    setUser(null);
    setPage('cards');
    showToast('Logged out successfully', 'info');
  }
  
  /* ========== OLD SINGLE JWT TOKEN LOGOUT (COMMENTED OUT) ==========
   * 
   * When we only had access token (no refresh):
   * 
   * async function handleLogout() {
   *   sessionStorage.removeItem('jwtToken');
   *   sessionStorage.removeItem('username');
   *   setUser(null);
   *   setPage('cards');
   *   showToast('Logged out successfully', 'info');
   * }
   * 
   * ========== END OF OLD SINGLE TOKEN LOGOUT ==========
   */
  
  /* ========== OLD HTTP BASIC AUTH LOGOUT (COMMENTED OUT) ==========
   * 
   * HTTP Basic Auth had a server-side logout endpoint:
   * 
   * async function handleLogout() {
   *   try {
   *     const authHeader = sessionStorage.getItem('authHeader');
   *     await fetch('/api/auth/logout', {
   *       method: 'POST',
   *       headers: authHeader ? { 'Authorization': authHeader } : {}
   *     });
   *   } catch (error) {
   *     console.error('Logout error:', error);
   *   }
   *   
   *   sessionStorage.removeItem('authHeader');
   *   sessionStorage.removeItem('username');
   *   setUser(null);
   *   setPage('cards');
   *   showToast('Logged out successfully', 'info');
   * }
   * 
   * ========== END OF OLD HTTP BASIC AUTH LOGOUT ==========
   */

  // Helper function to check if user has a specific role
  const hasRole = (role) => {
    if (!user || !user.authenticated) return false;
    return user.roles && user.roles.includes(role);
  };

  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 15 }}>
          {user && user.authenticated ? (
            <>
              <span style={{ fontSize: '0.9em', color: '#666' }}>
                Welcome, <strong>{user.username}</strong> ({user.roles.join(', ')})
              </span>
              <button onClick={handleLogout} style={{ padding: '5px 15px' }}>Logout</button>
            </>
          ) : (
            <>
              <span style={{ fontSize: '0.9em', color: '#666' }}>Browsing as Guest</span>
              <button onClick={() => setPage('login')} style={{ padding: '5px 15px', backgroundColor: '#007bff', color: 'white', border: 'none', borderRadius: 4, cursor: 'pointer' }}>Login</button>
            </>
          )}
        </div>
      </div>
      
      {page !== 'login' && (
        <nav style={{ marginBottom: 20 }}>
          <button onClick={() => setPage('cards')}>Cards</button>
          <button onClick={() => setPage('archetypes')}>Archetypes</button>
          <button onClick={() => setPage('decks')}>Decks</button>
          {hasRole('ADMIN') && (
            <button onClick={() => setPage('users')} style={{ backgroundColor: '#ff9800', color: 'white', border: 'none' }}>Users</button>
          )}
        </nav>
      )}

      {page === 'login' && <Login onLoginSuccess={handleLoginSuccess} onCancel={() => setPage('cards')} showToast={showToast} />}
      {page === 'cards' && <CardManager openCardDetail={openCardDetail} showToast={showToast} archetypeFilter={archetypeFilter} clearArchetypeFilter={() => setArchetypeFilter(null)} user={user} hasRole={hasRole} />}
      {page === 'archetypes' && <ArchetypeManager viewArchetypeCards={viewArchetypeCards} />}
      {page === 'decks' && <DeckManager showToast={showToast} user={user} hasRole={hasRole} />}
      {page === 'users' && <UsersManager />}
      {page === 'cardDetail' && selectedCard && (
        <CardDetail name={selectedCard} onBack={backToList} showToast={showToast} user={user} hasRole={hasRole} />
      )}
      <Toast message={toast.message} type={toast.type} onClose={() => setToast({ message: '', type: 'info' })} />
    </div>
  );
}

export default App;
