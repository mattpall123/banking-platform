package com.bank.backend.account.repository;

import com.bank.backend.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Pessimistic write lock — the underlying SQL is SELECT ... FOR UPDATE.
     * Other transactions trying to lock the same row WAIT until this
     * transaction commits.
     *
     * Required usage:
     *   - Caller must be inside an @Transactional method.
     *   - Always lock multiple accounts in DETERMINISTIC ID ORDER to prevent
     *     deadlocks (TransferService.transfer enforces this).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}