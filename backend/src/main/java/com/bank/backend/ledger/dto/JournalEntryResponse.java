package com.bank.backend.ledger.dto;

import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.domain.JournalEntryType;

import java.time.Instant;

public record JournalEntryResponse(
    Long id,
    String description,
    JournalEntryType entryType,
    String idempotencyKey,
    Instant occurredAt
) {
    public static JournalEntryResponse from(JournalEntry je) {
        return new JournalEntryResponse(
            je.getId(),
            je.getDescription(),
            je.getEntryType(),
            je.getIdempotencyKey(),
            je.getOccurredAt()
        );
    }
}