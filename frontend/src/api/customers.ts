// src/api/customers.ts

import { api } from "./client";

export interface CustomerResponse {
  id: number;
  legalFirstName: string;
  legalLastName: string;
  email: string;
  phone: string;
  city: string;
  province: string;
  kycStatus: "PENDING" | "APPROVED" | "REJECTED";
}

export const customersApi = {
  me() {
    return api<CustomerResponse>("/api/customers/me");
  },
};
