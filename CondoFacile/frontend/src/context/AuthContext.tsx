'use client';

import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthUser, clearToken, getTokenFromCookie, parseJwt, saveToken, loginRequest } from '@/lib/auth';

interface AuthContextValue {
  user: AuthUser | null;
  token: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  loading: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const storedToken = getTokenFromCookie();
    if (storedToken) {
      const parsed = parseJwt(storedToken);
      if (parsed) {
        setToken(storedToken);
        setUser(parsed);
      } else {
        clearToken();
      }
    }
    setLoading(false);
  }, []);

  const login = async (username: string, password: string) => {
    const result = await loginRequest(username, password);
    saveToken(result.token);
    setToken(result.token);
    setUser(result.user as AuthUser);
  };

  const logout = () => {
    clearToken();
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
