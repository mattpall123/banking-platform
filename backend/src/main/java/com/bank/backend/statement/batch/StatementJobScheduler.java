package com.bank.backend.statement.batch;

import com.bank.backend.statement.service.StatementJobLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * Scheduled trigger for the statement-generation batch job.
 *
 * Cron: "0 0 2 1 * *" → 02:00 on the 1st of every month, server local time.
 * (Format: second minute hour day month day-of-week.)
 *
 * Disabled by default in dev (see application.yml). Enable in prod by
 * setting bank.batch.statements.enabled=true.
 *
 * On the 1st of the month, we generate the PRECEDING month's statements
 * (since transactions from the just-ended month are now complete).
 */
@Component
@ConditionalOnProperty(name = "bank.batch.statements.enabled", havingValue = "true")
public class StatementJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatementJobScheduler.class);

    private final StatementJobLauncher launcher;
    private final String cronExpression;

    public StatementJobScheduler(
            StatementJobLauncher launcher,
            @Value("${bank.batch.statements.cron:0 0 2 1 * *}") String cronExpression
    ) {
        this.launcher = launcher;
        this.cronExpression = cronExpression;
        log.info("Statement batch scheduler enabled with cron='{}'", cronExpression);
    }

    /**
     * Reads the cron expression from the property at scheduling time.
     * The hard-coded fallback in the @Value is a safety net.
     */
    @Scheduled(cron = "${bank.batch.statements.cron:0 0 2 1 * *}")
    public void runMonthly() {
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        log.info("Cron-triggered statement run for {}", previousMonth);
        launcher.launch(previousMonth, null);  // null userId = system-triggered
    }
}