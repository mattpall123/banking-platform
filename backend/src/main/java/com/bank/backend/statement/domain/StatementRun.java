package com.bank.backend.statement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "statement_runs")
@Getter
@Setter
@NoArgsConstructor
public class StatementRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatementRunStatus status = StatementRunStatus.RUNNING;

    @Column(name = "statements_created", nullable = false)
    private Integer statementsCreated = 0;

    @Column(name = "error_count", nullable = false)
    private Integer errorCount = 0;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "triggered_by_user_id")
    private Long triggeredByUserId;
}