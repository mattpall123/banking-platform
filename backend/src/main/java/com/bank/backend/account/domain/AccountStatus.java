package com.bank.backend.account.domain;

public enum AccountStatus {
    PENDING,    // newly created, awaiting first deposit / activation
    ACTIVE,     // operational
    FROZEN,     // temporarily blocked (fraud hold, court order, etc.)
    CLOSED      // closed permanently — never deleted, retention is 5+ years
}