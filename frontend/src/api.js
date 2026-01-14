// Simple API utility for backend requests with JWT access token + refresh token support
// Backend always returns ResponseEnvelope: { success: boolean, message: string, data?: T }
// In development: Vite dev server proxies /api to backend service
// In production: Nginx proxies /api to backend service in-cluster
const API_BASE = '/api';

// Get authentication headers with JWT access token (Bearer token format)
function getAuthHeaders() {
  const accessToken = sessionStorage.getItem('accessToken');
  const headers = { 'Content-Type': 'application/json' };
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`; // JWT uses "Bearer" scheme
  }
  return headers;
}

// Refresh access token using refresh token
async function refreshAccessToken() {
  const refreshToken = sessionStorage.getItem('refreshToken');
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  
  try {
    const response = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    
    const result = await response.json();
    
    if (response.ok && result.success) {
      // Store new access token
      sessionStorage.setItem('accessToken', result.data.accessToken);
      // Update refresh token if rotated (optional)
      if (result.data.refreshToken) {
        sessionStorage.setItem('refreshToken', result.data.refreshToken);
      }
      return result.data.accessToken;
    } else {
      // Refresh token expired or invalid - clear storage and redirect to login
      sessionStorage.clear();
      throw new Error('Session expired - please login again');
    }
  } catch (error) {
    sessionStorage.clear();
    throw error;
  }
}

// Enhanced fetch with automatic token refresh on 401
async function fetchWithAuth(url, options = {}) {
  // First attempt with current access token
  let response = await fetch(url, options);
  
  // If 401 Unauthorized, try to refresh token and retry
  if (response.status === 401) {
    try {
      await refreshAccessToken();
      
      // Retry original request with new access token
      if (options.headers) {
        options.headers = getAuthHeaders(); // Get new Authorization header
      }
      response = await fetch(url, options);
    } catch (error) {
      // Refresh failed - user needs to login again
      console.error('Token refresh failed:', error);
      // Redirect to login or show login modal
      window.location.href = '/'; // Or dispatch event for login modal
      throw error;
    }
  }
  
  return response;
}

/* ========== OLD SINGLE TOKEN CODE (COMMENTED OUT) ==========
 * 
 * This was when we only had one JWT token (no refresh mechanism):
 * 
 * function getAuthHeaders() {
 *   const jwtToken = sessionStorage.getItem('jwtToken');
 *   const headers = { 'Content-Type': 'application/json' };
 *   if (jwtToken) {
 *     headers['Authorization'] = `Bearer ${jwtToken}`;
 *   }
 *   return headers;
 * }
 * 
 * ========== END OF OLD SINGLE TOKEN CODE ==========
 */

/* ========== OLD HTTP BASIC AUTH CODE (COMMENTED OUT) ==========
 * 
 * This was the previous approach that retrieved base64-encoded credentials:
 * 
 * function getAuthHeaders() {
 *   const authHeader = sessionStorage.getItem('authHeader');
 *   const headers = { 'Content-Type': 'application/json' };
 *   if (authHeader) {
 *     headers['Authorization'] = authHeader; // Was "Basic <base64>"
 *   }
 *   return headers;
 * }
 * 
 * ========== END OF OLD HTTP BASIC AUTH CODE ==========
 */

async function parseEnvelope(res) {
  try {
    const body = await res.json();
    return body;
  } catch (e) {
    return { success: res.ok, message: res.statusText };
  }
}

export async function fetchCards(page = 0, size = 20, query = '') {
  const params = new URLSearchParams();
  if (page !== undefined && page !== null) params.set('page', page);
  if (size !== undefined && size !== null) params.set('size', size);
  if (query && query.trim() !== '') params.set('query', query.trim());
  const res = await fetch(`${API_BASE}/cards?${params.toString()}`);
  return parseEnvelope(res);
}

export async function fetchUsers() {
  const res = await fetchWithAuth(`${API_BASE}/users`, {
    headers: getAuthHeaders()
  });
  return parseEnvelope(res);
}

export async function fetchCard(name) {
  const res = await fetch(`${API_BASE}/cards/by-name?name=${encodeURIComponent(name)}`);
  return parseEnvelope(res);
}

export async function createCard(card) {
  const res = await fetchWithAuth(`${API_BASE}/cards`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(card),
  });
  return parseEnvelope(res);
}

export async function updateCard(name, card) {
  const res = await fetchWithAuth(`${API_BASE}/cards/${encodeURIComponent(name)}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(card),
  });
  return parseEnvelope(res);
}

export async function deleteCard(name) {
  const res = await fetchWithAuth(`${API_BASE}/cards/${encodeURIComponent(name)}`, { 
    method: 'DELETE',
    headers: getAuthHeaders()
  });
  return parseEnvelope(res);
}

export async function fetchArchetypes() {
  const res = await fetch(`${API_BASE}/archetypes`);
  return parseEnvelope(res);
}

export async function fetchDecks() {
  const res = await fetch(`${API_BASE}/decks`);
  return parseEnvelope(res);
}

export async function createDeck(deck) {
  const res = await fetchWithAuth(`${API_BASE}/decks`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(deck),
  });
  return parseEnvelope(res);
}

export async function updateDeck(id, deck) {
  const res = await fetchWithAuth(`${API_BASE}/decks/${id}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(deck),
  });
  return parseEnvelope(res);
}

export async function deleteDeck(id) {
  const res = await fetchWithAuth(`${API_BASE}/decks/${id}`, { 
    method: 'DELETE',
    headers: getAuthHeaders()
  });
  return parseEnvelope(res);
}

export async function addCardToDeck(deckId, cardName) {
  const res = await fetchWithAuth(`${API_BASE}/decks/${deckId}/cards/${encodeURIComponent(cardName)}`, { 
    method: 'POST',
    headers: getAuthHeaders()
  });
  return parseEnvelope(res);
}

export async function removeCardFromDeck(deckId, cardName) {
  const res = await fetchWithAuth(`${API_BASE}/decks/${deckId}/cards/${encodeURIComponent(cardName)}`, { 
    method: 'DELETE',
    headers: getAuthHeaders()
  });
  return parseEnvelope(res);
}

// ===== Auth API Functions =====

export async function login(credentials) {
  const response = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(credentials)
  });
  return parseEnvelope(response);
}

export async function logout(refreshToken) {
  const response = await fetch(`${API_BASE}/auth/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken })
  });
  return parseEnvelope(response);
}

export async function getCurrentUser() {
  const response = await fetch(`${API_BASE}/auth/user`, {
    headers: getAuthHeaders()
  });
  return parseEnvelope(response);
}
