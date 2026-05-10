package com.bank.backend.etransfer.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request body for sending an e-transfer.
 *
 * If the recipient email has auto-deposit registered, securityQuestion
 * and securityAnswer are ignored (no Q&A needed). Otherwise they're
 * required and we 400 if missing.
 */
public record SendETransferRequest(
    @NotNull                                      Long sourceAccountId,
    @NotBlank @Email @Size(max = 255)             String recipientEmail,
    @NotBlank @Size(max = 140)                    String recipientName,
    @NotNull @DecimalMin(value = "0.01")         BigDecimal amount,
    @NotBlank @Pattern(regexp = "[A-Z]{3}")       String currency,
    @Size(max = 400)                              String message,

    // Optional — only required if recipient has no auto-deposit
    @Size(max = 140)                              String securityQuestion,
    @Size(min = 1, max = 100)                     String securityAnswer
) {}