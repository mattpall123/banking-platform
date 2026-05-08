package com.bank.backend.scheduled.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "scheduled_transfers")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "destination_account_number", nullable = false, length = 20)
    private String destinationAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 140)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;     // 1-7 if WEEKLY, null otherwise

    @Column(name = "day_of_month")
    private Integer dayOfMonth;    // 1-28 if MONTHLY, null otherwise

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledTransferStatus status = ScheduledTransferStatus.ACTIVE;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}