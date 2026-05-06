// src/api/auth.ts

import { api } from "./client";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  legalFirstName: string;
  legalLastName: string;
  dateOfBirth: string;       // YYYY-MM-DD
  phone: string;
  streetAddress: string;
  city: string;
  province: string;
  postalCode: string;
  occupation: string;
  idType: "DRIVERS_LICENCE" | "PASSPORT" | "PROVINCIAL_ID";
  idNumberLast4: string;
  idExpiryDate: string;      // YYYY-MM-DD
  pep: boolean;
}

export const authApi = {
  login(body: LoginRequest) {
    return api<AuthResponse>("/api/auth/login", {
      method: "POST",
      body,
      skipAuth: true,
    });
  },

  register(body: RegisterRequest) {
    return api<AuthResponse>("/api/auth/register", {
      method: "POST",
      body,
      skipAuth: true,
    });
  },

  logout(refreshToken: string) {
    return api<void>("/api/auth/logout", {
      method: "POST",
      body: { refreshToken },
    });
  },

  me() {
    return api<{ userId: number; email: string; roles: string[] }>("/api/auth/me");
  },
};