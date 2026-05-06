// src/auth/tokens.ts
//
// Token storage. We use localStorage for now (see ADR-001 in /docs/adr).
// XSS risk is acknowledged; migration path is HttpOnly cookies in Session 7.

const ACCESS_KEY = "bp.accessToken";
const REFRESH_KEY = "bp.refreshToken";

export const tokens = {
  getAccess(): string | null {
    return localStorage.getItem(ACCESS_KEY);
  },
  getRefresh(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string, refresh: string): void {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};