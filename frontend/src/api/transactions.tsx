// src/api/transactions.ts

import { api } from "./client";

export interface TransactionResponse {
  journalEntryId: number;
  description: string;
  entryType: "DEPOSIT" | "WITHDRAWAL" | "TRANSFER" | "ETRANSFER" | "INTEREST" | "FEE" | "REVERSAL" | "ADJUSTMENT" | "OPENING";
  signedAmount: number;
  currency: string;
  occurredAt: string;
}

export const transactionsApi = {
  forAccount(accountId: number) {
    return api<TransactionResponse[]>(`/api/transactions/me?accountId=${accountId}`);
  },
};