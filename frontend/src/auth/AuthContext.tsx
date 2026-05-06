// src/auth/AuthContext.tsx
// Provider + context only. The useAuth hook lives in useAuth.ts.

import { createContext, useEffect, useState, type ReactNode } from "react";
import { tokens } from "./tokens";
import { authApi, type LoginRequest, type RegisterRequest } from "@/api/auth";

export interface AuthUser {
  userId: number;
  email: string;
  roles: string[];
}

export interface AuthState {
  user: AuthUser | null;
  loading: boolean;
  login: (body: LoginRequest) => Promise<void>;
  register: (body: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(() => tokens.getAccess() !== null);

  useEffect(() => {
    if (!tokens.getAccess()) return;
    let cancelled = false;
    authApi
      .me()
      .then((u) => { if (!cancelled) setUser(u); })
      .catch(() => {
        tokens.clear();
        if (!cancelled) setUser(null);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  async function login(body: LoginRequest) {
    const res = await authApi.login(body);
    tokens.set(res.accessToken, res.refreshToken);
    const me = await authApi.me();
    setUser(me);
  }

  async function register(body: RegisterRequest) {
    const res = await authApi.register(body);
    tokens.set(res.accessToken, res.refreshToken);
    const me = await authApi.me();
    setUser(me);
  }

  async function logout() {
    const refresh = tokens.getRefresh();
    if (refresh) {
      try { await authApi.logout(refresh); } catch { /* best-effort */ }
    }
    tokens.clear();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}