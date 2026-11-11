import React, { createContext, useContext, useState, useEffect } from 'react';
import axiosClient from '../api/axiosClient';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [memberships, setMemberships] = useState([]);
  const [activeWorkspace, setActiveWorkspace] = useState(null);
  const [loading, setLoading] = useState(true);

  const fetchCurrentUser = async () => {
    const token = localStorage.getItem('veriweave_access_token');
    if (!token) {
      setLoading(false);
      return;
    }
    try {
      const res = await axiosClient.get('/auth/me');
      if (res.data?.success && res.data?.data) {
        const data = res.data.data;
        setUser(data.user);
        setMemberships(data.memberships || []);
        
        // Restore or pick active workspace
        const savedWsId = localStorage.getItem('veriweave_active_workspace_id');
        const match = data.memberships?.find((m) => String(m.id) === savedWsId);
        const selected = match || data.activeWorkspace || (data.memberships?.[0] ?? null);
        setActiveWorkspace(selected);
        if (selected) {
          localStorage.setItem('veriweave_active_workspace_id', String(selected.id));
        }
      }
    } catch (err) {
      console.error('Failed to load user session', err);
      logout();
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCurrentUser();

    const handleUnauthorized = () => logout();
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  const login = async (email, password) => {
    const res = await axiosClient.post('/auth/login', { email, password });
    if (res.data?.success && res.data?.data) {
      const data = res.data.data;
      localStorage.setItem('veriweave_access_token', data.accessToken);
      localStorage.setItem('veriweave_refresh_token', data.refreshToken);
      setUser(data.user);
      setMemberships(data.memberships || []);
      const selected = data.activeWorkspace || data.memberships?.[0] || null;
      setActiveWorkspace(selected);
      if (selected) {
        localStorage.setItem('veriweave_active_workspace_id', String(selected.id));
      }
      return data;
    }
    throw new Error(res.data?.message || 'Login failed');
  };

  const register = async (email, password, fullName, workspaceName) => {
    const res = await axiosClient.post('/auth/register', {
      email,
      password,
      fullName,
      workspaceName,
    });
    if (res.data?.success && res.data?.data) {
      const data = res.data.data;
      localStorage.setItem('veriweave_access_token', data.accessToken);
      localStorage.setItem('veriweave_refresh_token', data.refreshToken);
      setUser(data.user);
      setMemberships(data.memberships || []);
      const selected = data.activeWorkspace || data.memberships?.[0] || null;
      setActiveWorkspace(selected);
      if (selected) {
        localStorage.setItem('veriweave_active_workspace_id', String(selected.id));
      }
      return data;
    }
    throw new Error(res.data?.message || 'Registration failed');
  };

  const switchWorkspace = (workspace) => {
    setActiveWorkspace(workspace);
    if (workspace) {
      localStorage.setItem('veriweave_active_workspace_id', String(workspace.id));
    } else {
      localStorage.removeItem('veriweave_active_workspace_id');
    }
  };

  const logout = () => {
    localStorage.removeItem('veriweave_access_token');
    localStorage.removeItem('veriweave_refresh_token');
    localStorage.removeItem('veriweave_active_workspace_id');
    setUser(null);
    setMemberships([]);
    setActiveWorkspace(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        memberships,
        activeWorkspace,
        loading,
        login,
        register,
        logout,
        switchWorkspace,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
