package com.bank.backend.etransfer.service;

import com.bank.backend.etransfer.domain.ETransfer;
import com.bank.backend.etransfer.domain.ETransferStatus;
import com.bank.backend.etransfer.repository.ETransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional methods for e-transfer expiry.
 *
 * Lives separate from ETransferExpiryRunner for the same reason as
 * ScheduledTransferExecutor in Session 9: Spring's @Transactional uses
 * AOP proxies, and self-invocation bypasses the proxy. Calling
 * executor.method() from the runner goes through the proxy correctly.
 */
@Service
public class ETransferExpiryExecutor {

    private static final Logger log = LoggerFactory.getLogger(ETransferExpiryExecutor.class);

    private final ETransferRepository etransferRepo;
    private final ETransferService etransferService;

    public ETransferExpiryExecutor(
            ETransferRepository etransferRepo,
            ETransferService etransferService
    ) {
        this.etransferRepo = etransferRepo;
        this.etransferService = etransferService;
    }

    @Transactional
    public List<ETransfer> findDueForExpiry(Instant now) {
        return etransferRepo.findDueForExpiry(ETransferStatus.PENDING, now);
    }

    /**
     * Expire one transfer in its own transaction. REQUIRES_NEW so a
     * failure here doesn't roll back the others processed in the same
     * outer call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(ETransfer t) {
        // Re-read with lock — could have been claimed/cancelled in the
        // interval between findDueForExpiry returning and this call.
        ETransfer locked = etransferRepo.findByIdForUpdate(t.getId())
                .orElseThrow(() -> new IllegalStateException("Transfer vanished: " + t.getId()));
        if (locked.getStatus() != ETransferStatus.PENDING) {
            log.debug("Transfer {} no longer PENDING, skipping expiry", t.getId());
            return;
        }
        if (Instant.now().isBefore(locked.getExpiresAt())) {
            log.debug("Transfer {} not yet due, skipping expiry", t.getId());
            return;
        }

        etransferService.refundToSender(locked, "Expired");
        locked.setStatus(ETransferStatus.EXPIRED);
        locked.setUpdatedAt(Instant.now());
        etransferRepo.save(locked);

        log.info("e-Transfer EXPIRED: id={} amount={} {}", locked.getId(),
                locked.getAmount(), locked.getCurrency());
    }
}