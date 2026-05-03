package com.bank.backend.account.domain;

import com.bank.backend.customer.domain.Customer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Many-to-many join between Account and Customer, with a role and time
 * window (added_at / removed_at). We model this as an explicit @Entity
 * — NOT @ManyToMany — because the join row carries data.
 *
 * Joint account semantics live here: an account with multiple AccountHolder
 * rows where role = JOINT_OWNER is a joint account.
 *
 * Removing a holder sets removed_at; we do NOT delete the row, for audit
 * traceability.
 */
@Entity
@Table(name = "account_holders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HolderRole role;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "removed_at")
    private Instant removedAt;
}