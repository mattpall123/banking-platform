// src/api/accounts.ts

import { api } from "./client";

export type AccountType = "CHEQUING" | "SAVINGS" | "TFSA";
export type AccountStatus = "ACTIVE" | "FROZEN" | "CLOSED";

export interface AccountResponse {
  id: number;
  accountNumber: string;
  accountType: AccountType;
  currency: string;
  status: AccountStatus;
  balance: number;
  openedAt: string;
}

export interface JournalEntryResponse {
  id: number;
  description: string;
  entryType: "DEPOSIT" | "WITHDRAWAL" | "TRANSFER" | "INTEREST" | "FEE" | "REVERSAL" | "ADJUSTMENT" | "OPENING";
  idempotencyKey: string | null;
  occurredAt: string;
}

export const accountsApi = {
  listMine() {
    return api<AccountResponse[]>("/api/accounts/me");
  },

  deposit(accountId: number, amount: string, currency: string, idempotencyKey: string) {
    return api<JournalEntryResponse>(`/api/accounts/${accountId}/deposit`, {
      method: "POST",
      body: { amount, currency },
      idempotencyKey,
    });
  },

  withdraw(accountId: number, amount: string, currency: string, idempotencyKey: string) {
    return api<JournalEntryResponse>(`/api/accounts/${accountId}/withdraw`, {
      method: "POST",
      body: { amount, currency },
      idempotencyKey,
    });
  },
};