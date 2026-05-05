package com.bank.backend.account.service;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.domain.AccountStatus;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.domain.JournalEntryType;
import com.bank.backend.ledger.domain.LedgerAccount;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.service.LedgerService;
import com.bank.backend.ledger.service.PostingRequest;
import com.bank.backend.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Internal account-to-account transfers.
 *
 * Concurrency model: PESSIMISTIC LOCKING with deterministic lock ordering.
 *   1. We compute (smallerId, largerId) from the two accounts involved.
 *   2. We lock the smaller-id row first via SELECT ... FOR UPDATE,
 *      then the larger-id row.
 *   3. ANY other transfer involving the same pair locks them in the SAME
 *      order, so deadlock is impossible.
 *   4. The DB-level zero-sum trigger fires at commit time; if anything is
 *      wrong, the whole transfer rolls back.
 *
 * Ownership rules:
 *   - The authenticated user MUST be a holder of the source account.
 *   - The destination account just has to exist and be ACTIVE.
 *   - This matches real banks: anyone can send you money; only you can
 *     send your money.
 *
 * Idempotency: handled by LedgerService.post — same idempotency key returns
 * the existing journal entry without double-posting.
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepo;
    private final AccountHolderRepository accountHolderRepo;
    private final CustomerRepository customerRepo;
    private final LedgerAccountRepository ledgerAccountRepo;
    private final LedgerService ledger;

    public TransferService(
            AccountRepository accountRepo,
            AccountHolderRepository accountHolderRepo,
            CustomerRepository customerRepo,
            LedgerAccountRepository ledgerAccountRepo,
            LedgerService ledger
    ) {
        this.accountRepo = accountRepo;
        this.accountHolderRepo = accountHolderRepo;
        this.customerRepo = customerRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.ledger = ledger;
    }

    /**
     * Transfer money from a source account to a destination account number.
     *
     * @param sourceAccountId      source banking account id (auth user must be a holder)
     * @param destAccountNumber    destination account number (any active account)
     * @param amount               positive Money in the source account's currency
     * @param userId               authenticated user id (for ownership + audit)
     * @param idempotencyKey       optional client-supplied dedup key
     * @return the journal entry that recorded the transfer
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JournalEntry transfer(
            Long sourceAccountId,
            String destAccountNumber,
            Money amount,
            Long userId,
            String idempotencyKey
    ) {
        // Resolve the destination's id from its account number BEFORE locking.
        // We need both ids to compute deterministic lock order.
        Account destPreview = accountRepo.findByAccountNumber(destAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        if (sourceAccountId.equals(destPreview.getId())) {
            throw new IllegalArgumentException("Source and destination must differ");
        }

        // ---- DETERMINISTIC LOCK ORDERING ----
        // Always lock the lower id first. Any concurrent transfer involving
        // these two accounts locks them in the same order — no deadlock.
        Long firstId  = Math.min(sourceAccountId, destPreview.getId());
        Long secondId = Math.max(sourceAccountId, destPreview.getId());

        Account first  = accountRepo.findByIdForUpdate(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstId));
        Account second = accountRepo.findByIdForUpdate(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondId));

        // Re-bind source/dest from the locked rows.
        Account source = first.getId().equals(sourceAccountId) ? first : second;
        Account dest   = first.getId().equals(sourceAccountId) ? second : first;

        // ---- Validation under lock ----
        requireOwnership(source, userId);
        requireActive(source, "Source");
        requireActive(dest, "Destination");
        requireSameCurrency(source, dest, amount);
        requirePositive(amount);

        // ---- Balance check (now safe — row is locked) ----
        LedgerAccount sourceLedger = ledgerAccountRepo.findByBankingAccountId(source.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "No ledger account for source banking account " + source.getId()));
        LedgerAccount destLedger = ledgerAccountRepo.findByBankingAccountId(dest.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "No ledger account for dest banking account " + dest.getId()));

        // For a LIABILITY account, balance = -SUM(amount). Same as AccountService.
        Money sourceBalance = Money.of(
                ledger.balanceOf(sourceLedger.getId()).negate(),
                source.getCurrency()
        );

        if (sourceBalance.isLessThan(amount)) {
            throw new InsufficientFundsException(
                "Insufficient funds: balance " + sourceBalance + ", requested " + amount);
        }

        // ---- Post the journal entry ----
        // Sign convention: source's liability decreases (+amount on its
        // signed sum), dest's liability increases (-amount on its signed sum).
        // Sums to zero across the two postings.
        List<PostingRequest> postings = List.of(
                PostingRequest.of(sourceLedger.getId(), amount),         // +amount
                PostingRequest.of(destLedger.getId(),   amount.negate()) // -amount
        );

        JournalEntry je = ledger.post(
                "Transfer " + source.getAccountNumber() + " → " + dest.getAccountNumber(),
                JournalEntryType.TRANSFER,
                idempotencyKey,
                userId,
                postings
        );

        log.info("Transfer ok: src={} dst={} amount={} jeId={}",
                source.getAccountNumber(), dest.getAccountNumber(), amount, je.getId());

        return je;
    }

    // ---- guards ----

    private void requireOwnership(Account source, Long userId) {
        Customer customer = customerRepo.findByUserId(userId)
                .orElseThrow(() -> new AccessDeniedException("No customer for user"));

        boolean isHolder = accountHolderRepo.findByAccount(source).stream()
                .anyMatch(h -> h.getRemovedAt() == null
                        && h.getCustomer().getId().equals(customer.getId()));
        if (!isHolder) {
            throw new AccessDeniedException("Source account not found or not yours");
        }
    }

    private void requireActive(Account account, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException(label + " account is not active: " + account.getStatus());
        }
    }

    private void requireSameCurrency(Account source, Account dest, Money amount) {
        if (!source.getCurrency().equals(dest.getCurrency())) {
            throw new IllegalArgumentException(
                "Cross-currency transfers not supported (source=" + source.getCurrency() +
                ", dest=" + dest.getCurrency() + ")");
        }
        if (!source.getCurrency().equals(amount.currencyCode())) {
            throw new IllegalArgumentException(
                "Currency mismatch: account is " + source.getCurrency() +
                ", amount is " + amount.currencyCode());
        }
    }

    private void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}