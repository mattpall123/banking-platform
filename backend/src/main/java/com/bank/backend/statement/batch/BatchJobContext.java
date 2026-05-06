package com.bank.backend.statement.batch;

import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-launch context for the statement job. The launcher sets the period
 * before starting the job; the StatementJobConfig reads it when wiring
 * the StatementProcessor bean.
 *
 * Single-threaded by design — the job runs one at a time. If you ever
 * partition this for parallel execution you'd pass period via Spring
 * Batch's @StepScope + JobParameters instead.
 */
@Component
public class BatchJobContext {

    private final AtomicReference<YearMonth> period =
            new AtomicReference<>(YearMonth.now().minusMonths(1));

    public void setPeriod(YearMonth p) {
        period.set(p);
    }

    public YearMonth period() {
        return period.get();
    }
}