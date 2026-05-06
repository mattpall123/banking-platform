// src/api/statements.ts

import { api, ApiError } from "./client";
import { tokens } from "@/auth/tokens";

export interface StatementResponse {
  id: number;
  accountId: number;
  periodYear: number;
  periodMonth: number;
  transactionCount: number;
  openingBalance: number;
  closingBalance: number;
  currency: string;
  fileSizeBytes: number;
  generatedAt: string;
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const statementsApi = {
  listMine() {
    return api<StatementResponse[]>("/api/statements/me");
  },

  /**
   * Download a statement as a PDF blob and trigger a browser save.
   * We don't go through the typed `api()` helper because PDFs aren't JSON.
   */
  async download(statementId: number): Promise<void> {
    const token = tokens.getAccess();
    const res = await fetch(`${BASE_URL}/api/statements/${statementId}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });

    if (!res.ok) {
      let body: unknown = null;
      try { body = await res.json(); } catch { /* not JSON */ }
      throw new ApiError(res.status, body);
    }

    const blob = await res.blob();
    const filename = res.headers.get("X-Filename") ?? `statement-${statementId}.pdf`;

    // Trigger a download by clicking a synthetic <a download="...">.
    // Standard approach for blob-to-file in the browser.
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  },
};