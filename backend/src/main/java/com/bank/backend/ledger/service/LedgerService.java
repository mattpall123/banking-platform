package com.bank.backend.ledger.service;

import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.domain.JournalEntryType;
import com.bank.backend.ledger.domain.Posting;
import com.bank.backend.ledger.repository.JournalEntryRepository;
import com.bank.backend.ledger.repository.PostingRepository;
import com.bank.backend.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ONLY thing that writes postings. All deposit/withdraw/transfer flows
 * call into here.
 *
 * Validation pipeline:
 *   1. Idempotency check — if the key has been seen before, return the
 *      previously-persisted journal entry without writing new postings.
 *   2. Business validation — at least 2 postings, sums balance to zero
 *      per currency, no zero-amount postings.
 *   3. Persist journal_entry, then postings, all in one DB transaction.
 *      The DB constraint trigger re-verifies zero-sum at commit — defense
 *      in depth.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final JournalEntryRepository journalRepo;
    private final PostingRepository postingRepo;

    public LedgerService(JournalEntryRepository journalRepo, PostingRepository postingRepo) {
        this.journalRepo = journalRepo;
        this.postingRepo = postingRepo;
    }

    @Transactional
    public JournalEntry post(
            String description,
            JournalEntryType entryType,
            String idempotencyKey,
            Long createdByUserId,
            List<PostingRequest> postings
    ) {
        // 1. Idempotency: same key + already-persisted entry → return it
        if (idempotencyKey != null) {
            Optional<JournalEntry> existing = journalRepo.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay for key={} → returning existing journalEntryId={}",
                        idempotencyKey, existing.get().getId());
                return existing.get();
            }
        }

        // 2. Business validation
        validateBalanced(postings);

        // 3. Persist
        JournalEntry je = new JournalEntry();
        je.setDescription(description);
        je.setEntryType(entryType);
        je.setIdempotencyKey(idempotencyKey);
        je.setCreatedByUserId(createdByUserId);
        journalRepo.save(je);

        for (PostingRequest pr : postings) {
            Posting p = new Posting();
            p.setJournalEntryId(je.getId());
            p.setLedgerAccountId(pr.ledgerAccountId());
            p.setAmount(pr.signedAmount().amount());
            p.setCurrency(pr.signedAmount().currencyCode());
            postingRepo.save(p);
        }

        log.info("Posted journalEntryId={} type={} postings={}",
                je.getId(), entryType, postings.size());

        return je;
    }

    public BigDecimal balanceOf(Long ledgerAccountId) {
        BigDecimal raw = postingRepo.sumByLedgerAccountId(ledgerAccountId);
        return raw == null ? BigDecimal.ZERO : raw;
    }

    // ---- validation ----

    private void validateBalanced(List<PostingRequest> postings) {
        if (postings == null || postings.size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least two postings");
        }

        Map<String, BigDecimal> totalsByCurrency = new HashMap<>();
        for (PostingRequest pr : postings) {
            Money m = pr.signedAmount();
            if (m.isZero()) {
                throw new IllegalArgumentException("Zero-amount postings are not allowed");
            }
            totalsByCurrency.merge(m.currencyCode(), m.amount(), BigDecimal::add);
        }

        for (Map.Entry<String, BigDecimal> e : totalsByCurrency.entrySet()) {
            if (e.getValue().signum() != 0) {
                throw new IllegalArgumentException(
                    "Postings do not balance for currency " + e.getKey() +
                    ": net = " + e.getValue());
            }
        }
    }
}