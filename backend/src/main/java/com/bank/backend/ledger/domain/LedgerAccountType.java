package com.bank.backend.ledger.domain;

/**
 * Standard accounting categories.
 *
 * Sign convention (positive amount in our postings table means):
 *   ASSET, EXPENSE        → balance increases
 *   LIABILITY, EQUITY,
 *   REVENUE               → balance decreases
 *
 * Customer deposits are LIABILITY: from the bank's perspective, a deposit
 * is money the bank owes the customer. That's why on a real bank balance
 * sheet, "Customer Deposits" is on the liabilities side.
 */
public enum LedgerAccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}