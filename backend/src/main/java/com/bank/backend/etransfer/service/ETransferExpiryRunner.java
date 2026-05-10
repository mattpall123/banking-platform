package com.bank.backend.etransfer.service;

import com.bank.backend.etransfer.domain.ETransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Periodic e-transfer expiry runner.
 *
 * Real Interac expires transfers at 30 days. We default to 1 hour for
 * demo (configured via bank.etransfer.expiry-hours). The runner checks
 * every minute (configurable) and expires any PENDING transfers past
 * their expires_at.
 *
 * Disabled by default in tests via @ConditionalOnProperty.
 */
@Component
@ConditionalOnProperty(name = "bank.etransfer.expiry-runner.enabled", havingValue = "true")
public class ETransferExpiryRunner {

    private static final Logger log = LoggerFactory.getLogger(ETransferExpiryRunner.class);

    private final ETransferExpiryExecutor executor;

    public ETransferExpiryRunner(
            ETransferExpiryExecutor executor,
            @Value("${bank.etransfer.expiry-runner.fixed-rate-ms:60000}") long fixedRateMs
    ) {
        this.executor = executor;
        log.info("ETransferExpiryRunner enabled (fixedRateMs={})", fixedRateMs);
    }

    @Scheduled(fixedRateString = "${bank.etransfer.expiry-runner.fixed-rate-ms:60000}",
               initialDelayString = "${bank.etransfer.expiry-runner.initial-delay-ms:30000}")
    public void runExpiry() {
        Instant now = Instant.now();
        List<ETransfer> due = executor.findDueForExpiry(now);

        if (due.isEmpty()) {
            log.debug("No e-transfers due for expiry at {}", now);
            return;
        }

        log.info("Expiring {} e-transfers", due.size());

        int succeeded = 0;
        int failed = 0;
        for (ETransfer t : due) {
            try {
                executor.expireOne(t);
                succeeded++;
            } catch (Exception e) {
                log.error("Failed to expire e-transfer id={}: {}", t.getId(), e.getMessage(), e);
                failed++;
            }
        }
        log.info("Expiry run complete: {} expired, {} failed", succeeded, failed);
    }
}