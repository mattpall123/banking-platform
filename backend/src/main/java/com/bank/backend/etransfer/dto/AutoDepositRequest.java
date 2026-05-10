package com.bank.backend.etransfer.dto;

import jakarta.validation.constraints.NotNull;

public record AutoDepositRequest(
    @NotNull Long targetAccountId
) {}