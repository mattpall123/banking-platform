// src/api/etransfers.ts

import { api } from "./client";

export type ETransferStatus = "PENDING" | "COMPLETED" | "CANCELLED" | "EXPIRED" | "LOCKED";

export interface ETransferResponse {
  id: number;
  senderAccountId: number;
  recipientEmail: string;
  recipientName: string;
  recipientAccountId: number | null;
  amount: number;
  currency: string;
  message: string | null;
  securityQuestion: string | null;
  autoDeposited: boolean;
  status: ETransferStatus;
  expiresAt: string;
  createdAt: string;
}

export interface IncomingETransferResponse extends ETransferResponse {
  wrongAttempts: number;
  attemptsRemaining: number;
}

export interface SendETransferRequest {
  sourceAccountId: number;
  recipientEmail: string;
  recipientName: string;
  amount: number;
  currency: string;
  message?: string | null;
  securityQuestion?: string | null;
  securityAnswer?: string | null;
}

export interface ClaimETransferRequest {
  depositAccountId: number;
  securityAnswer: string;
}

export interface AutoDepositResponse {
  id: number;
  email: string;
  targetAccountId: number;
  enabled: boolean;
}

export const etransfersApi = {
  send(req: SendETransferRequest, idempotencyKey?: string) {
    return api<ETransferResponse>("/api/etransfers/send", {
      method: "POST",
      body: req,
      idempotencyKey,
    });
  },

  outgoing() {
    return api<ETransferResponse[]>("/api/etransfers/outgoing");
  },

  incoming() {
    return api<IncomingETransferResponse[]>("/api/etransfers/incoming");
  },

  claim(id: number, req: ClaimETransferRequest) {
    return api<ETransferResponse>(`/api/etransfers/${id}/claim`, {
      method: "POST",
      body: req,
    });
  },

  cancel(id: number) {
    return api<ETransferResponse>(`/api/etransfers/${id}/cancel`, {
      method: "POST",
    });
  },

  getAutoDeposit() {
    return api<AutoDepositResponse | null>("/api/etransfers/auto-deposit").catch((e: unknown) => {
      // 404 → not registered, return null
      if (e instanceof Error && "status" in e && (e as { status: number }).status === 404) {
        return null;
      }
      throw e;
    });
  },

  setAutoDeposit(targetAccountId: number) {
    return api<AutoDepositResponse>("/api/etransfers/auto-deposit", {
      method: "PUT",
      body: { targetAccountId },
    });
  },

  disableAutoDeposit() {
    return api<void>("/api/etransfers/auto-deposit", { method: "DELETE" });
  },
};