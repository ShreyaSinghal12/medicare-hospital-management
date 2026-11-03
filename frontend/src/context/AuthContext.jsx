import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { toast } from 'sonner';
import { setUnauthorizedHandler } from '../api/client.js';
import * as authApi from '../services/authService.js';
import { initCsrf } from '../services/systemService.js';

const AuthContext = createContext(null);

/**
 * Holds the logged-in user (from GET /api/auth/me). The session itself lives in an
 * HTTP-only cookie managed by the server; nothing sensitive is kept in browser storage.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [initializing, setInitializing] = useState(true);
  const userRef = useRef(null);
  userRef.current = user;

  const refresh = useCallback(async () => {
    try {
      const me = await authApi.fetchCurrentUser();
      setUser(me);
      return me;
    } catch {
      setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    (async () => {
      await initCsrf().catch(() => {});
      await refresh();
      setInitializing(false);
    })();
  }, [refresh]);

  // Any 401 from a protected API means the session is gone (timeout, revoked, logged out elsewhere).
  useEffect(() => {
    setUnauthorizedHandler((error) => {
      if (userRef.current) {
        setUser(null);
        toast.error(
          error.code === 'SESSION_REVOKED'
            ? error.message
            : 'Your session has expired. Please log in again.',
        );
      }
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  const login = useCallback(async (email, password) => {
    const me = await authApi.login(email, password);
    setUser(me);
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, initializing, isAuthenticated: !!user, login, logout, refresh, setUser }),
    [user, initializing, login, logout, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
