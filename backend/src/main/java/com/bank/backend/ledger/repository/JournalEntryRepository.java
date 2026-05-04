package com.bank.backend.ledger.repository;

import com.bank.backend.ledger.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey);
}