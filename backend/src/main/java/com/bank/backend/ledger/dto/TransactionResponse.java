package com.bank.backend.ledger.dto;

import com.bank.backend.ledger.domain.JournalEntryType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    Long journalEntryId,
    String description,
    JournalEntryType entryType,
    BigDecimal signedAmount,    // signed from the account's perspective: + means money in, - means money out
    String currency,
    Instant occurredAt
) {}