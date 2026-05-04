package com.bank.backend.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single signed line item under a journal entry.
 *
 * Sign convention: positive amount = increase the natural side of this
 * account. For LIABILITY accounts (like customer deposits), positive
 * means balance went UP (more money owed to customer). For ASSET accounts
 * (like Cash), positive means balance went UP (more cash on hand).
 *
 * The DB-level constraint trigger ensures all postings under one
 * journal_entry sum to zero per currency.
 */
@Entity
@Table(name = "postings")
@Getter
@Setter
@NoArgsConstructor
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_entry_id", nullable = false)
    private Long journalEntryId;

    @Column(name = "ledger_account_id", nullable = false)
    private Long ledgerAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}