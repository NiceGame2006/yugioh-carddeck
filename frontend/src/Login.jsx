import React, { useState } from 'react';
import { login } from './api';

export default function Login({ onLoginSuccess, onCancel, showToast }) {
  const [credentials, setCredentials] = useState({ username: '', password: '' });
  const [loading, setLoading] = useState(false);

  const handleChange = (e) => {
    setCredentials(prev => ({ ...prev, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      // JWT Authentication: Send credentials in request body
      const result = await login({
        username: credentials.username,
        password: credentials.password
      });

      if (result.success) {
        // Store both JWT access token and refresh token
        sessionStorage.setItem('accessToken', result.data.accessToken);
        sessionStorage.setItem('refreshToken', result.data.refreshToken);
        sessionStorage.setItem('username', result.data.username);
        
        if (showToast) showToast(`Welcome, ${result.data.username}!`, 'info');
        if (onLoginSuccess) onLoginSuccess(result.data);
      } else {
        if (showToast) showToast(result.message || 'Login failed', 'error');
      }
    } catch (error) {
      console.error('Login error:', error);
      if (showToast) showToast('Login failed: ' + (error.message || 'Network error'), 'error');
    } finally {
      setLoading(false);
    }
  };
  
  /* ========== OLD JWT SINGLE TOKEN CODE (COMMENTED OUT) ==========
   * 
   * This was when we only had one JWT token (no refresh token):
   * 
   * sessionStorage.setItem('jwtToken', result.data.token);
   * 
   * ========== END OF OLD SINGLE TOKEN CODE ==========
   */
  
  /* ========== OLD HTTP BASIC AUTH CODE (COMMENTED OUT) ==========
   * 
   * This was the previous approach that sent base64-encoded credentials:
   * 
   * const authHeader = 'Basic ' + btoa(credentials.username + ':' + credentials.password);
   * 
   * const response = await fetch('/api/auth/login', {
   *   method: 'POST',
   *   headers: {
   *     'Authorization': authHeader,
   *     'Content-Type': 'application/json'
   *   }
   * });
   * 
   * sessionStorage.setItem('authHeader', authHeader);
   * 
   * sessionStorage.setItem('authHeader', authHeader);
   * 
   * ========== END OF OLD HTTP BASIC AUTH CODE ==========
   */

  return (
    <div style={{ maxWidth: 400, margin: '50px auto', padding: 20, border: '1px solid #ccc', borderRadius: 8, backgroundColor: '#f9f9f9' }}>
      <h2 style={{ textAlign: 'center', marginBottom: 20 }}>Login</h2>
      
      <div style={{ marginBottom: 20, padding: 15, backgroundColor: '#e3f2fd', borderRadius: 4, fontSize: '0.9em' }}>
        <strong>Demo Accounts:</strong>
        <div style={{ marginTop: 10 }}>
          <strong>Users:</strong> 
          <br />
          <code>user1</code> / <code>password1</code> or <code>user2</code> / <code>password2</code>
          <br />
          <small>Can create/manage own decks</small>
        </div>
        <div style={{ marginTop: 10 }}>
          <strong>Admins:</strong> 
          <br />
          <code>admin1</code> / <code>password1</code> or <code>admin2</code> / <code>password2</code>
          <br />
          <small>Can create/delete cards and manage all decks</small>
        </div>
      </div>

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 15 }}>
          <label style={{ display: 'block', marginBottom: 5, fontWeight: 'bold' }}>Username</label>
          <input 
            type="text" 
            name="username" 
            value={credentials.username} 
            onChange={handleChange}
            required
            style={{ width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc' }}
          />
        </div>
        
        <div style={{ marginBottom: 20 }}>
          <label style={{ display: 'block', marginBottom: 5, fontWeight: 'bold' }}>Password</label>
          <input 
            type="password" 
            name="password" 
            value={credentials.password} 
            onChange={handleChange}
            required
            style={{ width: '100%', padding: 8, borderRadius: 4, border: '1px solid #ccc' }}
          />
        </div>
        
        <div style={{ display: 'flex', gap: 10 }}>
          <button 
            type="button"
            onClick={onCancel}
            style={{ 
              flex: 1,
              padding: 10, 
              backgroundColor: '#6c757d', 
              color: 'white', 
              border: 'none', 
              borderRadius: 4, 
              fontSize: '1em',
              cursor: 'pointer'
            }}
          >
            Cancel
          </button>
          <button 
            type="submit" 
            disabled={loading}
            style={{ 
              flex: 1,
              padding: 10, 
              backgroundColor: loading ? '#ccc' : '#007bff', 
              color: 'white', 
              border: 'none', 
              borderRadius: 4, 
              fontSize: '1em',
              cursor: loading ? 'not-allowed' : 'pointer'
            }}
          >
            {loading ? 'Logging in...' : 'Login'}
          </button>
        </div>
      </form>

      <div style={{ marginTop: 15, textAlign: 'center', fontSize: '0.9em', color: '#666' }}>
        <p>Browse as guest (read-only access)</p>
      </div>
    </div>
  );
}
