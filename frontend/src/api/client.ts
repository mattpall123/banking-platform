// src/api/client.ts
//
// Thin fetch wrapper that:
//   1. Prepends the API base URL
//   2. Attaches Authorization: Bearer <accessToken> if logged in
//   3. On 401, tries to refresh the access token once and retries the request
//   4. Throws a typed ApiError on non-2xx responses

import { tokens } from "@/auth/tokens";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(status: number, body: unknown, message?: string) {
    super(message ?? `API error ${status}`);
    this.status = status;
    this.body = body;
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  skipAuth?: boolean;
  skipRefresh?: boolean;
  idempotencyKey?: string;
}

export async function api<T = unknown>(
  path: string,
  opts: RequestOptions = {}
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(opts.headers ?? {}),
  };

  if (!opts.skipAuth) {
    const token = tokens.getAccess();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  if (opts.idempotencyKey) {
    headers["Idempotency-Key"] = opts.idempotencyKey;
  }

  const init: RequestInit = {
    method: opts.method ?? "GET",
    headers,
  };

  if (opts.body !== undefined) {
    init.body = JSON.stringify(opts.body);
  }

  const url = `${BASE_URL}${path}`;
  let res = await fetch(url, init);

  if (res.status === 401 && !opts.skipRefresh && tokens.getRefresh()) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      headers.Authorization = `Bearer ${refreshed}`;
      res = await fetch(url, { ...init, headers });
    }
  }

  if (!res.ok) {
    let body: unknown = null;
    try { body = await res.json(); } catch { /* not JSON */ }
    throw new ApiError(res.status, body);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

async function tryRefresh(): Promise<string | null> {
  const refresh = tokens.getRefresh();
  if (!refresh) return null;

  try {
    const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: refresh }),
    });
    if (!res.ok) {
      tokens.clear();
      return null;
    }
    const data = (await res.json()) as { accessToken: string; refreshToken: string };
    tokens.set(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch {
    tokens.clear();
    return null;
  }
}