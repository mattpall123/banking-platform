package com.bank.backend.statement.dto;

import com.bank.backend.statement.domain.Statement;

import java.math.BigDecimal;
import java.time.Instant;

public record StatementResponse(
    Long id,
    Long accountId,
    Integer periodYear,
    Integer periodMonth,
    Integer transactionCount,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    String currency,
    Long fileSizeBytes,
    Instant generatedAt
) {
    public static StatementResponse from(Statement s) {
        return new StatementResponse(
                s.getId(), s.getAccountId(),
                s.getPeriodYear(), s.getPeriodMonth(),
                s.getTransactionCount(),
                s.getOpeningBalance(), s.getClosingBalance(),
                s.getCurrency(),
                s.getFileSizeBytes(), s.getGeneratedAt()
        );
    }
}