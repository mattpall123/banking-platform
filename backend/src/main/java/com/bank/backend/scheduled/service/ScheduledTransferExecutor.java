package com.bank.backend.scheduled.service;

import com.bank.backend.account.service.InsufficientFundsException;
import com.bank.backend.account.service.TransferService;
import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.scheduled.domain.ExecutionStatus;
import com.bank.backend.scheduled.domain.Frequency;
import com.bank.backend.scheduled.domain.ScheduledTransfer;
import com.bank.backend.scheduled.domain.ScheduledTransferExecution;
import com.bank.backend.scheduled.domain.ScheduledTransferStatus;
import com.bank.backend.scheduled.repository.ScheduledTransferExecutionRepository;
import com.bank.backend.scheduled.repository.ScheduledTransferRepository;
import com.bank.backend.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Transactional methods for scheduled-transfer execution.
 *
 * Why this lives separate from ScheduledTransferRunner: Spring's
 * @Transactional works via AOP proxies, and self-invocation (this.method())
 * BYPASSES the proxy. So when the @Scheduled method called another
 * @Transactional method on the same bean, no transaction was opened →
 * pessimistic locks blew up with TransactionRequiredException.
 *
 * Splitting into two beans means runDue() calls executor.findDueWithLock(),
 * which goes through the proxy, and the transaction is properly opened.
 */
@Service
public class ScheduledTransferExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferExecutor.class);

    private final ScheduledTransferRepository scheduledRepo;
    private final ScheduledTransferExecutionRepository execRepo;
    private final TransferService transferService;

    public ScheduledTransferExecutor(
            ScheduledTransferRepository scheduledRepo,
            ScheduledTransferExecutionRepository execRepo,
            TransferService transferService
    ) {
        this.scheduledRepo = scheduledRepo;
        this.execRepo = execRepo;
        this.transferService = transferService;
    }

    /**
     * Find due schedules with PESSIMISTIC_WRITE lock. Required to be in a
     * transaction (locks need one). The lock is released when this method
     * returns; per-schedule processing happens in fresh transactions via
     * executeOne so we don't pin all locks for the duration of a long run.
     */
    @Transactional
    public List<ScheduledTransfer> findDueWithLock(Instant now) {
        return scheduledRepo.findDue(ScheduledTransferStatus.ACTIVE, now);
    }

    /**
     * Execute one due schedule in its own transaction.
     *
     * REQUIRES_NEW so a failure here doesn't roll back other schedules
     * processed in the same outer call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeOne(ScheduledTransfer st, Instant now) {
        Instant plannedAt = st.getNextRunAt();
        String idemKey = idempotencyKey(st.getId(), plannedAt);

        // Idempotency: have we already fired this exact point?
        if (execRepo.findByIdempotencyKey(idemKey).isPresent()) {
            log.warn("Schedule {} planned-at {} already executed (idem hit); advancing nextRunAt only",
                    st.getId(), plannedAt);
            advanceOrExpire(st, plannedAt);
            return;
        }

        ExecutionStatus status;
        Long journalEntryId = null;
        String errorMessage = null;

        try {
            JournalEntry je = transferService.transfer(
                    st.getSourceAccountId(),
                    st.getDestinationAccountNumber(),
                    Money.of(st.getAmount(), st.getCurrency()),
                    st.getCreatedByUserId(),
                    idemKey
            );
            status = ExecutionStatus.SUCCESS;
            journalEntryId = je.getId();
            log.info("Scheduled transfer fired: scheduleId={} jeId={} amount={} {} src={} dest={}",
                    st.getId(), je.getId(), st.getAmount(), st.getCurrency(),
                    st.getSourceAccountId(), st.getDestinationAccountNumber());

        } catch (InsufficientFundsException e) {
            status = ExecutionStatus.INSUFFICIENT_FUNDS;
            errorMessage = e.getMessage();
            log.warn("Scheduled transfer scheduleId={} insufficient funds: {}", st.getId(), e.getMessage());

        } catch (Exception e) {
            status = ExecutionStatus.FAILED;
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("Scheduled transfer scheduleId={} failed: {}", st.getId(), errorMessage);
        }

        ScheduledTransferExecution exec = new ScheduledTransferExecution();
        exec.setScheduledTransferId(st.getId());
        exec.setPlannedAt(plannedAt);
        exec.setExecutedAt(Instant.now());
        exec.setStatus(status);
        exec.setJournalEntryId(journalEntryId);
        exec.setErrorMessage(errorMessage == null ? null : truncate(errorMessage, 500));
        exec.setIdempotencyKey(idemKey);
        execRepo.save(exec);

        advanceOrExpire(st, plannedAt);
    }

    private void advanceOrExpire(ScheduledTransfer st, Instant lastFiredAt) {
        if (st.getFrequency() == Frequency.ONCE) {
            st.setStatus(ScheduledTransferStatus.EXPIRED);
            st.setUpdatedAt(Instant.now());
            scheduledRepo.save(st);
            return;
        }

        Instant searchFrom = lastFiredAt.plusMillis(1);
        Instant nextRunAt = FrequencyMath.computeNextRun(st, searchFrom);

        if (st.getEndDate() != null) {
            LocalDate nextDate = nextRunAt.atZone(ZoneId.systemDefault()).toLocalDate();
            if (nextDate.isAfter(st.getEndDate())) {
                st.setStatus(ScheduledTransferStatus.EXPIRED);
                st.setUpdatedAt(Instant.now());
                scheduledRepo.save(st);
                log.info("Schedule {} expired (past endDate)", st.getId());
                return;
            }
        }

        st.setNextRunAt(nextRunAt);
        st.setUpdatedAt(Instant.now());
        scheduledRepo.save(st);
        log.debug("Schedule {} advanced: nextRunAt={}", st.getId(), nextRunAt);
    }

    private static String idempotencyKey(Long scheduleId, Instant plannedAt) {
        return "scheduled-" + scheduleId + "-" + plannedAt.toEpochMilli();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}