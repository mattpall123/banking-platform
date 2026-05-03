package com.bank.backend.account.domain;

import com.bank.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A banking product (chequing, savings, TFSA).
 *
 * The Account row is the *container* — the actual money lives in the ledger
 * (Session 4). A balance shown on the dashboard is a derived projection
 * over journal entries, never stored as the source of truth on this row.
 *
 * Accounts are NEVER deleted. Closing transitions status -> CLOSED and sets
 * closed_at; the row is retained for the regulatory minimum (5 years post-
 * relationship in Canada under FINTRAC).
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The human-facing identifier (e.g., "100000000123"). Distinct from id.
     * We use id for joins and account_number for displays / external APIs.
     * Never expose id in URLs or responses.
     */
    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(nullable = false, length = 3)
    private String currency = "CAD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.PENDING;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}