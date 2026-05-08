package com.bank.backend.scheduled.dto;

import com.bank.backend.scheduled.domain.Frequency;
import com.bank.backend.scheduled.domain.ScheduledTransfer;
import com.bank.backend.scheduled.domain.ScheduledTransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ScheduledTransferResponse(
    Long id,
    Long sourceAccountId,
    String destinationAccountNumber,
    BigDecimal amount,
    String currency,
    String description,
    Frequency frequency,
    Integer dayOfWeek,
    Integer dayOfMonth,
    LocalDate startDate,
    LocalDate endDate,
    Instant nextRunAt,
    ScheduledTransferStatus status,
    Instant createdAt
) {
    public static ScheduledTransferResponse from(ScheduledTransfer s) {
        return new ScheduledTransferResponse(
            s.getId(), s.getSourceAccountId(), s.getDestinationAccountNumber(),
            s.getAmount(), s.getCurrency(), s.getDescription(),
            s.getFrequency(), s.getDayOfWeek(), s.getDayOfMonth(),
            s.getStartDate(), s.getEndDate(), s.getNextRunAt(),
            s.getStatus(), s.getCreatedAt()
        );
    }
}