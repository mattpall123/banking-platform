package com.bank.backend.ledger.domain;

public enum JournalEntryType {

    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    ETRANSFER,    // Interac e-Transfer (hold or settlement leg)
    INTEREST,
    FEE,
    REVERSAL,
    ADJUSTMENT,
    OPENING
}