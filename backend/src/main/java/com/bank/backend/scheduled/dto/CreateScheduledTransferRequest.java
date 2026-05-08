package com.bank.backend.scheduled.dto;

import com.bank.backend.scheduled.domain.Frequency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateScheduledTransferRequest(
    @NotNull                                         Long sourceAccountId,
    @NotBlank @Pattern(regexp = "\\d{12}")           String destinationAccountNumber,
    @NotNull @DecimalMin(value = "0.01")            BigDecimal amount,
    @NotBlank @Pattern(regexp = "[A-Z]{3}")          String currency,
    @NotBlank @Size(max = 140)                       String description,
    @NotNull                                         Frequency frequency,
    @Min(1) @Max(7)                                  Integer dayOfWeek,
    @Min(1) @Max(28)                                 Integer dayOfMonth,
    @NotNull                                         LocalDate startDate,
                                                     LocalDate endDate
) {}