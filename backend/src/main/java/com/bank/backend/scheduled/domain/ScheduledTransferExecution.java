package com.bank.backend.scheduled.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scheduled_transfer_executions")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledTransferExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheduled_transfer_id", nullable = false)
    private Long scheduledTransferId;

    @Column(name = "planned_at", nullable = false)
    private Instant plannedAt;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionStatus status;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;
}