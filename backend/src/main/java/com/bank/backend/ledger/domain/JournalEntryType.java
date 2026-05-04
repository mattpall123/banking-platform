package com.bank.backend.ledger.domain;

public enum JournalEntryType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    INTEREST,
    FEE,
    REVERSAL,
    ADJUSTMENT,
    OPENING       // bootstrap entries for the chart of accounts
}