package com.bank.backend.scheduled.service;

import com.bank.backend.scheduled.domain.ScheduledTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodic scheduled-transfer executor — top-level orchestrator only.
 *
 * Real work happens in ScheduledTransferExecutor, a separate bean. This
 * separation is required because Spring's @Transactional uses AOP proxies,
 * and self-invocation of @Transactional methods on the same bean BYPASSES
 * the proxy. Calling executor.method() goes through the proxy correctly.
 *
 * Disabled by default in dev via the same feature-flag pattern as the
 * statement scheduler.
 */
@Component
@ConditionalOnProperty(name = "bank.scheduled-transfers.runner.enabled", havingValue = "true")
public class ScheduledTransferRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferRunner.class);

    private final ScheduledTransferExecutor executor;

    public ScheduledTransferRunner(
            ScheduledTransferExecutor executor,
            @Value("${bank.scheduled-transfers.runner.fixed-rate-ms:300000}") long fixedRateMs
    ) {
        this.executor = executor;
        log.info("ScheduledTransferRunner enabled (fixedRateMs={})", fixedRateMs);
    }

    @Scheduled(fixedRateString = "${bank.scheduled-transfers.runner.fixed-rate-ms:300000}",
               initialDelayString = "${bank.scheduled-transfers.runner.initial-delay-ms:30000}")
    public void runDue() {
        Instant now = Instant.now();
        List<ScheduledTransfer> due = executor.findDueWithLock(now);

        if (due.isEmpty()) {
            log.debug("No scheduled transfers due at {}", now);
            return;
        }

        log.info("Found {} due scheduled transfers at {}", due.size(), now);

        int succeeded = 0;
        int failed = 0;
        for (ScheduledTransfer st : due) {
            try {
                executor.executeOne(st, now);
                succeeded++;
            } catch (Exception e) {
                log.error("Unexpected error processing scheduled transfer id={}: {}",
                        st.getId(), e.getMessage(), e);
                failed++;
            }
        }
        log.info("Scheduled-transfer run complete: {} succeeded, {} failed", succeeded, failed);
    }
}