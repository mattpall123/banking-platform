package com.bank.backend.ledger.domain;

import com.bank.backend.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ledger_accounts")
@Getter
@Setter
@NoArgsConstructor
public class LedgerAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable, human-readable code. e.g. "CASH", "EQUITY:OPENING", "DEPOSIT:100000000001". */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private LedgerAccountType accountType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "parent_id")
    private Long parentId;

    /** Optional link to a banking Account from V2. Null for GL accounts (Cash, Equity). */
    @Column(name = "banking_account_id")
    private Long bankingAccountId;
}