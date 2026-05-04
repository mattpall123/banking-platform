package com.bank.backend.ledger.service;

import com.bank.backend.shared.Money;

/**
 * Internal struct: "post this signed Money to this ledger account".
 * Used by services to build a balanced journal entry before handing it
 * to LedgerService.
 */
public record PostingRequest(Long ledgerAccountId, Money signedAmount) {

    public static PostingRequest of(Long ledgerAccountId, Money signedAmount) {
        return new PostingRequest(ledgerAccountId, signedAmount);
    }
}