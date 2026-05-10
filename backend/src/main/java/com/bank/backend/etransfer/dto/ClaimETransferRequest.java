package com.bank.backend.etransfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for a manual claim. depositAccountId is which of the recipient's
 * accounts to credit (recipients with multiple accounts pick one).
 */
public record ClaimETransferRequest(
    @NotNull                          Long depositAccountId,
    @NotBlank @Size(min = 1, max = 100) String securityAnswer
) {}