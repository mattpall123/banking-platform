// src/api/transfers.ts

import { api } from "./client";
import type { JournalEntryResponse } from "./accounts";

export const transfersApi = {
  transfer(
    sourceAccountId: number,
    destinationAccountNumber: string,
    amount: string,
    currency: string,
    idempotencyKey: string
  ) {
    return api<JournalEntryResponse>("/api/transfers", {
      method: "POST",
      body: {
        sourceAccountId,
        destinationAccountNumber,
        amount,
        currency,
      },
      idempotencyKey,
    });
  },
};