package com.bank.backend.scheduled.service;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.scheduled.domain.Frequency;
import com.bank.backend.scheduled.domain.ScheduledTransfer;
import com.bank.backend.scheduled.domain.ScheduledTransferStatus;
import com.bank.backend.scheduled.dto.CreateScheduledTransferRequest;
import com.bank.backend.scheduled.repository.ScheduledTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ScheduledTransferService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTransferService.class);

    private final ScheduledTransferRepository repo;
    private final AccountRepository accountRepo;
    private final AccountHolderRepository holderRepo;
    private final CustomerRepository customerRepo;

    public ScheduledTransferService(
            ScheduledTransferRepository repo,
            AccountRepository accountRepo,
            AccountHolderRepository holderRepo,
            CustomerRepository customerRepo
    ) {
        this.repo = repo;
        this.accountRepo = accountRepo;
        this.holderRepo = holderRepo;
        this.customerRepo = customerRepo;
    }

    @Transactional
    public ScheduledTransfer create(CreateScheduledTransferRequest req, Long userId) {
        // Ownership check on the source account: same loadOwnedAccount pattern
        // we use everywhere else.
        Account source = accountRepo.findById(req.sourceAccountId())
                .orElseThrow(() -> new AccessDeniedException("Account not found or not yours"));
        assertOwnership(source, userId);

        // Validate frequency-specific fields
        validateFrequencyFields(req);

        ScheduledTransfer st = new ScheduledTransfer();
        st.setSourceAccountId(source.getId());
        st.setDestinationAccountNumber(req.destinationAccountNumber());
        st.setAmount(req.amount());
        st.setCurrency(req.currency().toUpperCase());
        st.setDescription(req.description());
        st.setFrequency(req.frequency());
        st.setDayOfWeek(req.dayOfWeek());
        st.setDayOfMonth(req.dayOfMonth());
        st.setStartDate(req.startDate());
        st.setEndDate(req.endDate());
        st.setStatus(ScheduledTransferStatus.ACTIVE);
        st.setCreatedByUserId(userId);
        st.setCreatedAt(Instant.now());
        st.setUpdatedAt(Instant.now());

        // Compute first run
        st.setNextRunAt(FrequencyMath.computeNextRun(st, Instant.now()));

        ScheduledTransfer saved = repo.save(st);
        log.info("Created scheduled transfer id={} userId={} freq={} nextRun={}",
                saved.getId(), userId, saved.getFrequency(), saved.getNextRunAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ScheduledTransfer> listForUser(Long userId) {
        Customer customer = customerRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No customer for user"));

        List<Long> ownedIds = holderRepo.findByCustomer(customer).stream()
                .filter(h -> h.getRemovedAt() == null)
                .map(AccountHolder::getAccount)
                .map(Account::getId)
                .toList();

        if (ownedIds.isEmpty()) return List.of();
        return repo.findBySourceAccountIdInOrderByNextRunAtAsc(ownedIds);
    }

    @Transactional
    public ScheduledTransfer cancel(Long scheduledTransferId, Long userId) {
        ScheduledTransfer st = repo.findById(scheduledTransferId)
                .orElseThrow(() -> new AccessDeniedException("Not found or not yours"));

        // Only the creator can cancel. Tellers/admins could also be allowed
        // — that's a future hardening pass.
        if (!st.getCreatedByUserId().equals(userId)) {
            throw new AccessDeniedException("Not found or not yours");
        }

        if (st.getStatus() == ScheduledTransferStatus.CANCELLED) {
            return st;   // idempotent
        }

        st.setStatus(ScheduledTransferStatus.CANCELLED);
        st.setUpdatedAt(Instant.now());
        log.info("Cancelled scheduled transfer id={} userId={}", st.getId(), userId);
        return repo.save(st);
    }

    // ---- helpers ----

    private void assertOwnership(Account account, Long userId) {
        Customer customer = customerRepo.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("Account not found or not yours"));
        boolean owns = holderRepo.findByAccount(account).stream()
                .anyMatch(h -> h.getRemovedAt() == null
                        && h.getCustomer().getId().equals(customer.getId()));
        if (!owns) {
            throw new AccessDeniedException("Account not found or not yours");
        }
    }

    private void validateFrequencyFields(CreateScheduledTransferRequest req) {
        switch (req.frequency()) {
            case ONCE -> {
                if (req.dayOfWeek() != null || req.dayOfMonth() != null) {
                    throw new IllegalArgumentException("ONCE schedule must not specify dayOfWeek or dayOfMonth");
                }
            }
            case DAILY -> {
                if (req.dayOfWeek() != null || req.dayOfMonth() != null) {
                    throw new IllegalArgumentException("DAILY schedule must not specify dayOfWeek or dayOfMonth");
                }
            }
            case WEEKLY -> {
                if (req.dayOfWeek() == null) {
                    throw new IllegalArgumentException("WEEKLY schedule requires dayOfWeek (1-7)");
                }
                if (req.dayOfMonth() != null) {
                    throw new IllegalArgumentException("WEEKLY schedule must not specify dayOfMonth");
                }
            }
            case MONTHLY -> {
                if (req.dayOfMonth() == null) {
                    throw new IllegalArgumentException("MONTHLY schedule requires dayOfMonth (1-28)");
                }
                if (req.dayOfWeek() != null) {
                    throw new IllegalArgumentException("MONTHLY schedule must not specify dayOfWeek");
                }
            }
        }
    }
}