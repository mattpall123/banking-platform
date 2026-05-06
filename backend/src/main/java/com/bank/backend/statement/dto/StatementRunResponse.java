package com.bank.backend.statement.dto;

import com.bank.backend.statement.domain.StatementRun;
import com.bank.backend.statement.domain.StatementRunStatus;

import java.time.Instant;

public record StatementRunResponse(
    Long id,
    Integer periodYear,
    Integer periodMonth,
    Instant startedAt,
    Instant finishedAt,
    StatementRunStatus status,
    Integer statementsCreated,
    Integer errorCount,
    String errorMessage,
    Long triggeredByUserId
) {
    public static StatementRunResponse from(StatementRun r) {
        return new StatementRunResponse(
                r.getId(), r.getPeriodYear(), r.getPeriodMonth(),
                r.getStartedAt(), r.getFinishedAt(), r.getStatus(),
                r.getStatementsCreated(), r.getErrorCount(),
                r.getErrorMessage(), r.getTriggeredByUserId()
        );
    }
}