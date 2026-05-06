package com.bank.backend.statement.service;

import com.bank.backend.statement.batch.BatchJobContext;
import com.bank.backend.statement.domain.StatementRun;
import com.bank.backend.statement.domain.StatementRunStatus;
import com.bank.backend.statement.repository.StatementRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;

/**
 * Orchestrates a statement-generation run:
 *   1. Create a StatementRun row in RUNNING state
 *   2. Set the BatchJobContext to the requested period
 *   3. Launch the Spring Batch Job
 *   4. Update the StatementRun row based on the job outcome
 *
 * The Spring Batch metadata tables (BATCH_JOB_EXECUTION etc.) capture
 * detailed execution info. Our StatementRun row is the high-level
 * customer-facing summary.
 */
@Service
public class StatementJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(StatementJobLauncher.class);

    private final JobLauncher jobLauncher;
    private final Job statementGenerationJob;
    private final StatementRunRepository runRepo;
    private final BatchJobContext context;

    public StatementJobLauncher(
            JobLauncher jobLauncher,
            Job statementGenerationJob,
            StatementRunRepository runRepo,
            BatchJobContext context
    ) {
        this.jobLauncher = jobLauncher;
        this.statementGenerationJob = statementGenerationJob;
        this.runRepo = runRepo;
        this.context = context;
    }

    /**
     * Launch the job for the given period synchronously. Returns the
     * StatementRun row recording the outcome.
     */
    public StatementRun launch(YearMonth period, Long triggeredByUserId) {
        StatementRun run = new StatementRun();
        run.setPeriodYear(period.getYear());
        run.setPeriodMonth(period.getMonthValue());
        run.setTriggeredByUserId(triggeredByUserId);
        run = persist(run);

        try {
            context.setPeriod(period);

            // Spring Batch dedups runs by JobParameters. We add a launchTimestamp
            // so each launch is unique even for the same period.
            JobParameters params = new JobParametersBuilder()
                    .addString("period", period.toString())
                    .addLong("launchTimestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution exec = jobLauncher.run(statementGenerationJob, params);

            int written = (int) exec.getStepExecutions().stream()
                    .mapToLong(se -> se.getWriteCount()).sum();
            int errors = (int) exec.getStepExecutions().stream()
                    .mapToLong(se -> se.getSkipCount() + se.getWriteSkipCount()).sum();

            run.setStatementsCreated(written);
            run.setErrorCount(errors);
            run.setStatus(exec.getStatus().isUnsuccessful()
                    ? StatementRunStatus.FAILED
                    : StatementRunStatus.COMPLETED);
            run.setFinishedAt(Instant.now());
            if (exec.getStatus().isUnsuccessful() && exec.getAllFailureExceptions() != null
                    && !exec.getAllFailureExceptions().isEmpty()) {
                for (Throwable t : exec.getAllFailureExceptions()) {
                    log.error("Batch failure: {}", exec.getStatus(), t);   // log full stack
                    Throwable root = t;
                    while (root.getCause() != null && root.getCause() != root) {
                        root = root.getCause();
                    }
                    log.error("  root cause: {}: {}", root.getClass().getName(), root.getMessage());
                }
                Throwable t = exec.getAllFailureExceptions().get(0);
                run.setErrorMessage(truncate(t.getClass().getSimpleName() + ": " + t.getMessage(), 2000));
            }
            return persist(run);
        } catch (Exception e) {
            log.error("Statement job failed for {}", period, e);
            run.setStatus(StatementRunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            run.setErrorMessage(truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 2000));
            return persist(run);
        }
    }

    @Transactional
    public StatementRun persist(StatementRun run) {
        return runRepo.save(run);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}