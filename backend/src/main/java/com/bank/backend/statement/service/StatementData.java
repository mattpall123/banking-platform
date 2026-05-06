package com.bank.backend.statement.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * All the data needed to render one statement PDF. Computed by the batch
 * job's processor; consumed by the renderer.
 */
public record StatementData(
    String customerName,
    String accountNumber,
    String accountType,
    String currency,
    LocalDate periodStart,    // inclusive
    LocalDate periodEnd,      // inclusive
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    List<Line> lines
) {
    /** One line of the transaction list. signedAmount is from the customer's perspective. */
    public record Line(
        Instant occurredAt,
        String description,
        BigDecimal signedAmount,
        BigDecimal runningBalance
    ) {}
}