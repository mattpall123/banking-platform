package com.bank.backend.ledger.repository;

import com.bank.backend.ledger.domain.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {
    Optional<LedgerAccount> findByCode(String code);
    Optional<LedgerAccount> findByBankingAccountId(Long bankingAccountId);
}