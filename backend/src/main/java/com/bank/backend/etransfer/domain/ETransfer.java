package com.bank.backend.etransfer.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "etransfers")
@Getter
@Setter
@NoArgsConstructor
public class ETransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_account_id", nullable = false)
    private Long senderAccountId;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "recipient_name", nullable = false, length = 140)
    private String recipientName;

    @Column(name = "recipient_account_id")
    private Long recipientAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 400)
    private String message;

    @Column(name = "security_question", length = 140)
    private String securityQuestion;

    @Column(name = "security_answer_hash", length = 100)
    private String securityAnswerHash;

    @Column(name = "auto_deposited", nullable = false)
    private Boolean autoDeposited = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ETransferStatus status = ETransferStatus.PENDING;

    @Column(name = "holding_journal_entry_id", nullable = false)
    private Long holdingJournalEntryId;

    @Column(name = "settlement_journal_entry_id")
    private Long settlementJournalEntryId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}