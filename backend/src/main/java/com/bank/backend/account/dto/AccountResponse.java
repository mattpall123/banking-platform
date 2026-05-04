package com.bank.backend.account.dto;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountStatus;
import com.bank.backend.account.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
    Long id,
    String accountNumber,
    AccountType accountType,
    String currency,
    AccountStatus status,
    BigDecimal balance,            // computed from the ledger
    Instant openedAt
) {
    public static AccountResponse of(Account a, BigDecimal balance) {
        return new AccountResponse(
            a.getId(),
            a.getAccountNumber(),
            a.getAccountType(),
            a.getCurrency(),
            a.getStatus(),
            balance,
            a.getOpenedAt()
        );
    }
}