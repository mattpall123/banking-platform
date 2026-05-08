// src/api/scheduledTransfers.ts

import { api } from "./client";

export type Frequency = "ONCE" | "DAILY" | "WEEKLY" | "MONTHLY";
export type ScheduledTransferStatus = "ACTIVE" | "PAUSED" | "EXPIRED" | "CANCELLED";

export interface ScheduledTransferResponse {
  id: number;
  sourceAccountId: number;
  destinationAccountNumber: string;
  amount: number;
  currency: string;
  description: string;
  frequency: Frequency;
  dayOfWeek: number | null;
  dayOfMonth: number | null;
  startDate: string;
  endDate: string | null;
  nextRunAt: string;
  status: ScheduledTransferStatus;
  createdAt: string;
}

export interface CreateScheduledTransferRequest {
  sourceAccountId: number;
  destinationAccountNumber: string;
  amount: number;
  currency: string;
  description: string;
  frequency: Frequency;
  dayOfWeek?: number | null;
  dayOfMonth?: number | null;
  startDate: string;
  endDate?: string | null;
}

export const scheduledTransfersApi = {
  listMine() {
    return api<ScheduledTransferResponse[]>("/api/scheduled-transfers/me");
  },

  create(req: CreateScheduledTransferRequest) {
    return api<ScheduledTransferResponse>("/api/scheduled-transfers", {
      method: "POST",
      body: req,   // pass the object — api() will stringify
    });
  },

  cancel(id: number) {
    return api<ScheduledTransferResponse>(`/api/scheduled-transfers/${id}`, {
      method: "DELETE",
    });
  },
};