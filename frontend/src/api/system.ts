// src/api/system.ts
//
// Calls Spring Actuator endpoints. /health and /info are public — they
// don't need auth.

export interface HealthResponse {
  status: "UP" | "DOWN" | "UNKNOWN";
}

export interface InfoResponse {
  app?: {
    name?: string;
    description?: string;
    version?: string;
  };
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const systemApi = {
  async health(): Promise<HealthResponse> {
    const res = await fetch(`${BASE_URL}/actuator/health`);
    if (!res.ok) return { status: "DOWN" };
    return res.json();
  },

  async info(): Promise<InfoResponse> {
    const res = await fetch(`${BASE_URL}/actuator/info`);
    if (!res.ok) return {};
    return res.json();
  },
};