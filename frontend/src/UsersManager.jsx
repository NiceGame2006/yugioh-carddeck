import React, { useEffect, useState } from 'react';
import { fetchUsers } from './api';

export default function UsersManager() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadUsers();
  }, []);

  async function loadUsers() {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchUsers();
      if (result.success) {
        setUsers(result.data || []);
      } else {
        setError(result.message || 'Failed to load users');
      }
    } catch (err) {
      console.error('Error loading users:', err);
      setError('Failed to load users: ' + (err.message || ''));
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return <div><h2>Users</h2><p>Loading users...</p></div>;
  }

  if (error) {
    return (
      <div>
        <h2>Users</h2>
        <div style={{ padding: 10, backgroundColor: '#ffebee', color: '#c62828', borderRadius: 4 }}>
          <p style={{ margin: 0 }}>{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h2>Users Management</h2>
      <p>Total users: {users.length}</p>
      
      <table border="1" cellPadding="8" style={{ width: '100%', marginTop: 20 }}>
        <thead>
          <tr style={{ backgroundColor: '#f5f5f5' }}>
            <th>ID</th>
            <th>Username</th>
            <th>Role</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {users.map(user => (
            <tr key={user.id}>
              <td>{user.id}</td>
              <td><strong>{user.username}</strong></td>
              <td>
                <span style={{ 
                  padding: '3px 8px', 
                  borderRadius: 4, 
                  backgroundColor: user.role === 'ROLE_ADMIN' ? '#e3f2fd' : '#f3e5f5',
                  color: user.role === 'ROLE_ADMIN' ? '#1976d2' : '#7b1fa2',
                  fontSize: '0.9em'
                }}>
                  {user.role === 'ROLE_ADMIN' ? 'ADMIN' : 'USER'}
                </span>
              </td>
              <td>
                <span style={{ 
                  color: user.enabled ? '#2e7d32' : '#d32f2f',
                  fontWeight: 'bold'
                }}>
                  {user.enabled ? '✓ Active' : '✗ Disabled'}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
