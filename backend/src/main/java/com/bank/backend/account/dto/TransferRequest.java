package com.bank.backend.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for POST /api/transfers.
 *
 * Source is identified by its banking account ID (caller must own it).
 * Destination is identified by its account number (caller does NOT need
 * to own it — anyone can send money to anyone, like real banks).
 */
public record TransferRequest(
    @NotNull
    Long sourceAccountId,

    @NotBlank
    @Size(max = 20)
    String destinationAccountNumber,

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    BigDecimal amount,

    @NotBlank
    @Pattern(regexp = "CAD|USD", message = "Currency must be CAD or USD")
    String currency
) {}