package com.bank.backend.ledger.repository;

import com.bank.backend.ledger.domain.Posting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    List<Posting> findByJournalEntryId(Long journalEntryId);

    List<Posting> findByLedgerAccountIdOrderByCreatedAtDesc(Long ledgerAccountId);

    /**
     * Compute the current balance of a ledger account by summing every
     * posting against it. The "balance" is fully derived from the ledger;
     * we never store it as a separate column.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
          FROM Posting p
         WHERE p.ledgerAccountId = :ledgerAccountId
    """)
    BigDecimal sumByLedgerAccountId(@Param("ledgerAccountId") Long ledgerAccountId);
    /**
     * Every posting against a ledger account, joined with its journal entry,
     * newest first. Used for transaction history.
     */
    @Query("""
        SELECT p, je
          FROM Posting p
          JOIN JournalEntry je ON je.id = p.journalEntryId
         WHERE p.ledgerAccountId = :ledgerAccountId
         ORDER BY je.occurredAt DESC, p.id DESC
    """)
    List<Object[]> findHistoryForLedgerAccount(@Param("ledgerAccountId") Long ledgerAccountId);
}