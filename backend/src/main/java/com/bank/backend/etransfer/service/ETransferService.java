package com.bank.backend.etransfer.service;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.domain.AccountStatus;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.account.service.InsufficientFundsException;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.etransfer.domain.AutoDepositSetting;
import com.bank.backend.etransfer.domain.ETransfer;
import com.bank.backend.etransfer.domain.ETransferStatus;
import com.bank.backend.etransfer.dto.ClaimETransferRequest;
import com.bank.backend.etransfer.dto.IncomingETransferResponse;
import com.bank.backend.etransfer.dto.SendETransferRequest;
import com.bank.backend.etransfer.repository.AutoDepositSettingRepository;
import com.bank.backend.etransfer.repository.ETransferRepository;
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
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.bank.backend.etransfer.domain.ETransferAttempt;
import com.bank.backend.etransfer.repository.ETransferAttemptRepository;
import com.bank.backend.user.domain.UserAccount;
import com.bank.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Interac e-Transfer simulation.
 *
 * SEND FLOW:
 *   1. Validate ownership + balance
 *   2. Look up auto-deposit setting for recipient email
 *   3a. AUTO-DEPOSIT: debit sender → credit recipient's account directly.
 *       Create ETransfer with autoDeposited=true, status=COMPLETED, both
 *       journal entry FKs set.
 *   3b. NO AUTO-DEPOSIT: debit sender → credit Pending ledger account.
 *       Create ETransfer with autoDeposited=false, status=PENDING, only
 *       holdingJournalEntryId set. securityQuestion/Answer required.
 *
 * Concurrency: pessimistic lock on the source account (same as TransferService).
 *
 * The PENDING ledger account holds funds in flight. From the bank's books
 * perspective, money never leaves — it just moves between sub-accounts of
 * the LIABILITY side until it lands.
 */
@Service
public class ETransferService {

    private static final Logger log = LoggerFactory.getLogger(ETransferService.class);
    private static final String PENDING_ETRANSFERS_CODE = "PENDING_ETRANSFERS";
    private final ETransferAttemptRepository attemptRepo;
    private final UserAccountRepository userRepo;
    private final AccountRepository accountRepo;
    private final AccountHolderRepository accountHolderRepo;
    private final CustomerRepository customerRepo;
    private final LedgerAccountRepository ledgerAccountRepo;
    private final LedgerService ledger;
    private final ETransferRepository etransferRepo;
    private final AutoDepositSettingRepository autoDepositRepo;
    private final ETransferProperties props;

    public ETransferService(
            AccountRepository accountRepo,
            AccountHolderRepository accountHolderRepo,
            CustomerRepository customerRepo,
            LedgerAccountRepository ledgerAccountRepo,
            LedgerService ledger,
            ETransferRepository etransferRepo,
            AutoDepositSettingRepository autoDepositRepo,
            ETransferAttemptRepository attemptRepo,
            UserAccountRepository userRepo,
            ETransferProperties props
    ) {
        this.accountRepo = accountRepo;
        this.accountHolderRepo = accountHolderRepo;
        this.customerRepo = customerRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.ledger = ledger;
        this.etransferRepo = etransferRepo;
        this.autoDepositRepo = autoDepositRepo;
        this.attemptRepo = attemptRepo;
        this.userRepo = userRepo;
        this.props = props;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ETransfer send(SendETransferRequest req, Long userId, String idempotencyKey) {
        // ---- 1. Lock source account ----
        Account source = accountRepo.findByIdForUpdate(req.sourceAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        // ---- 2. Ownership ----
        requireOwnership(source, userId);
        requireActive(source);

        // ---- 3. Currency + amount validation ----
        Money amount = Money.of(req.amount(), req.currency().toUpperCase());
        if (!source.getCurrency().equals(amount.currencyCode())) {
            throw new IllegalArgumentException(
                "Currency mismatch: account is " + source.getCurrency() + ", amount is " + amount.currencyCode());
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // ---- 4. Auto-deposit lookup ----
        Optional<AutoDepositSetting> autoDeposit = autoDepositRepo.findByEmail(req.recipientEmail())
                .filter(a -> Boolean.TRUE.equals(a.getEnabled()));

        // ---- 5. Validate Q&A presence (only required if no auto-deposit) ----
        if (autoDeposit.isEmpty()) {
            if (req.securityQuestion() == null || req.securityQuestion().isBlank()) {
                throw new IllegalArgumentException("Security question is required (recipient has no auto-deposit)");
            }
            if (req.securityAnswer() == null || req.securityAnswer().isBlank()) {
                throw new IllegalArgumentException("Security answer is required (recipient has no auto-deposit)");
            }
        }
        
        if (autoDeposit.isPresent() && autoDeposit.get().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot send an e-Transfer to your own auto-deposit email");
        }


        // ---- 6. Balance check ----
        LedgerAccount sourceLedger = ledgerAccountRepo.findByBankingAccountId(source.getId())
                .orElseThrow(() -> new IllegalStateException("No ledger account for source"));
        Money sourceBalance = Money.of(
            ledger.balanceOf(sourceLedger.getId()).negate(),    // LIABILITY: balance = -SUM
            source.getCurrency()
        );
        if (sourceBalance.isLessThan(amount)) {
            throw new InsufficientFundsException(
                "Insufficient funds: balance " + sourceBalance + ", requested " + amount);
        }

        // ---- 7. Choose path ----
        if (autoDeposit.isPresent()) {
            return sendAutoDeposit(source, sourceLedger, amount, req, userId, idempotencyKey, autoDeposit.get());
        } else {
            return sendPending(source, sourceLedger, amount, req, userId, idempotencyKey);
        }
    }

    /**
     * Auto-deposit path: lock destination, debit sender, credit destination,
     * single journal entry, ETransfer marked COMPLETED.
     */
    private ETransfer sendAutoDeposit(
            Account source,
            LedgerAccount sourceLedger,
            Money amount,
            SendETransferRequest req,
            Long userId,
            String idempotencyKey,
            AutoDepositSetting autoDeposit
    ) {
        // Lock the destination account too. Deterministic order (smaller id first)
        // to avoid deadlocks with other transfers in flight.
        Long firstId  = Math.min(source.getId(), autoDeposit.getTargetAccountId());
        Long secondId = Math.max(source.getId(), autoDeposit.getTargetAccountId());
        accountRepo.findByIdForUpdate(firstId)
                .orElseThrow(() -> new IllegalStateException("Account vanished: " + firstId));
        Account dest = accountRepo.findByIdForUpdate(secondId)
                .orElseThrow(() -> new IllegalStateException("Account vanished: " + secondId));
        if (!dest.getId().equals(autoDeposit.getTargetAccountId())) {
            // We locked source first; rebind dest from the actual target lookup
            dest = accountRepo.findByIdForUpdate(autoDeposit.getTargetAccountId())
                    .orElseThrow(() -> new IllegalStateException("Target account vanished"));
        }

        requireActive(dest);
        if (!dest.getCurrency().equals(amount.currencyCode())) {
            throw new IllegalArgumentException(
                "Recipient's auto-deposit account currency mismatch: " + dest.getCurrency());
        }

        LedgerAccount destLedger = ledgerAccountRepo.findByBankingAccountId(dest.getId())
                .orElseThrow(() -> new IllegalStateException("No ledger account for dest"));

        // Single journal entry: debit source, credit destination
        List<PostingRequest> postings = List.of(
                PostingRequest.of(sourceLedger.getId(), amount),         // +amount
                PostingRequest.of(destLedger.getId(),   amount.negate()) // -amount
        );

        JournalEntry je = ledger.post(
                "e-Transfer (auto-deposit) " + source.getAccountNumber() + " → " + dest.getAccountNumber(),
                JournalEntryType.ETRANSFER,
                idempotencyKey,
                userId,
                postings
        );

        ETransfer t = newPendingShell(req, source, userId, amount);
        t.setRecipientAccountId(dest.getId());
        t.setAutoDeposited(true);
        t.setSecurityQuestion(null);
        t.setSecurityAnswerHash(null);
        t.setStatus(ETransferStatus.COMPLETED);
        t.setHoldingJournalEntryId(je.getId());
        t.setSettlementJournalEntryId(je.getId());   // same JE — auto-deposit collapses send + claim
        ETransfer saved = etransferRepo.save(t);

        log.info("e-Transfer auto-deposited: id={} src={} dest={} amount={} jeId={}",
                saved.getId(), source.getAccountNumber(), dest.getAccountNumber(), amount, je.getId());
        return saved;
    }

    /**
     * Pending path: debit source, credit Pending ledger account, ETransfer
     * marked PENDING with security Q&A.
     */
    private ETransfer sendPending(
            Account source,
            LedgerAccount sourceLedger,
            Money amount,
            SendETransferRequest req,
            Long userId,
            String idempotencyKey
    ) {
        LedgerAccount pendingLedger = ledgerAccountRepo.findByCode(PENDING_ETRANSFERS_CODE)
                .orElseThrow(() -> new IllegalStateException(
                    "PENDING_ETRANSFERS ledger account missing — V14 migration not applied?"));

        // Debit source, credit Pending
        List<PostingRequest> postings = List.of(
                PostingRequest.of(sourceLedger.getId(),  amount),         // +amount (LIABILITY decreases)
                PostingRequest.of(pendingLedger.getId(), amount.negate()) // -amount (LIABILITY increases)
        );

        JournalEntry je = ledger.post(
                "e-Transfer hold from " + source.getAccountNumber() + " to " + req.recipientEmail(),
                JournalEntryType.ETRANSFER,
                idempotencyKey,
                userId,
                postings
        );

        ETransfer t = newPendingShell(req, source, userId, amount);
        t.setRecipientAccountId(null);     // unknown until claim
        t.setAutoDeposited(false);
        t.setSecurityQuestion(req.securityQuestion().trim());
        t.setSecurityAnswerHash(hashAnswer(req.securityAnswer()));
        t.setStatus(ETransferStatus.PENDING);
        t.setHoldingJournalEntryId(je.getId());
        t.setSettlementJournalEntryId(null);
        ETransfer saved = etransferRepo.save(t);

        log.info("e-Transfer pending: id={} src={} recipient={} amount={} jeId={}",
                saved.getId(), source.getAccountNumber(), req.recipientEmail(), amount, je.getId());
        return saved;
    }

    /** Common fields for both paths. The path-specific fields are set by the caller. */
    private ETransfer newPendingShell(SendETransferRequest req, Account source, Long userId, Money amount) {
        ETransfer t = new ETransfer();
        t.setSenderAccountId(source.getId());
        t.setSenderUserId(userId);
        t.setRecipientEmail(req.recipientEmail().toLowerCase().trim());
        t.setRecipientName(req.recipientName().trim());
        t.setAmount(amount.amount());
        t.setCurrency(amount.currencyCode());
        t.setMessage(req.message());
        t.setExpiresAt(Instant.now().plus(props.expiryHours(), ChronoUnit.HOURS));
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    /**
     * Hash a security answer using BCrypt. Trimmed + lower-cased so users
     * can answer "Whiskers" / "WHISKERS" / "whiskers " interchangeably —
     * matches real Interac UX.
     */
    public static String hashAnswer(String rawAnswer) {
        String normalized = rawAnswer.trim().toLowerCase();
        return BCrypt.hashpw(normalized, BCrypt.gensalt(10));
    }

    /** Used by the claim flow in Part 3. */
    public static boolean checkAnswer(String rawAnswer, String hash) {
        if (rawAnswer == null || hash == null) return false;
        String normalized = rawAnswer.trim().toLowerCase();
        try {
            return BCrypt.checkpw(normalized, hash);
        } catch (IllegalArgumentException e) {
            // hash format invalid
            return false;
        }
    }

    // ---- guards (mirror TransferService patterns) ----

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

    private void requireActive(Account a) {
        if (a.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active: " + a.getStatus());
        }
    }

    /**
     * List incoming pending e-transfers for the user's email.
     * Used by GET /api/etransfers/incoming.
     */
    @Transactional(readOnly = true)
    public List<IncomingETransferResponse> listIncoming(Long userId) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<ETransfer> rows = etransferRepo.findByRecipientEmailAndStatus(
                user.getEmail(), ETransferStatus.PENDING);

        return rows.stream()
                .map(e -> IncomingETransferResponse.from(
                        e, attemptRepo.countByEtransferIdAndCorrectFalse(e.getId())))
                .toList();
    }

    /**
     * Claim a pending e-transfer.
     *
     * Flow:
     *   1. Lock the etransfer row (PESSIMISTIC_WRITE)
     *   2. Verify it's PENDING and not expired (else throw appropriate)
     *   3. Verify the user's email matches the recipient
     *   4. Verify the depositAccount belongs to the user
     *   5. Hash-compare the supplied answer
     *      - WRONG: record attempt, count wrongs, throw SecurityAnswerWrongException
     *      - 3rd WRONG in a row: mark LOCKED, refund to sender, throw
     *      - CORRECT: lock both Pending and recipient accounts (deterministic order),
     *        post settlement journal entry, mark COMPLETED, return updated row
     */
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        noRollbackFor = SecurityAnswerWrongException.class
    )
    public ETransfer claim(Long etransferId, ClaimETransferRequest req, Long userId, String ipAddress) {
        ETransfer t = etransferRepo.findByIdForUpdate(etransferId)
                .orElseThrow(() -> new AccessDeniedException("Transfer not found"));

        // Status validations
        if (t.getStatus() != ETransferStatus.PENDING) {
            throw new IllegalStateException("Transfer is not pending; status=" + t.getStatus());
        }
        if (Instant.now().isAfter(t.getExpiresAt())) {
            throw new IllegalStateException("Transfer has expired");
        }
        if (Boolean.TRUE.equals(t.getAutoDeposited())) {
            throw new IllegalStateException("Auto-deposited transfer cannot be claimed manually");
        }

        // Recipient must match the authenticated user's email
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        if (!user.getEmail().equalsIgnoreCase(t.getRecipientEmail())) {
            throw new AccessDeniedException("This transfer is not addressed to you");
        }

        // Verify the deposit account belongs to the user
        Account destAccount = accountRepo.findById(req.depositAccountId())
                .orElseThrow(() -> new AccessDeniedException("Deposit account not found"));
        requireOwnership(destAccount, userId);
        requireActive(destAccount);
        if (!destAccount.getCurrency().equals(t.getCurrency())) {
            throw new IllegalArgumentException(
                "Currency mismatch: transfer is " + t.getCurrency() +
                ", deposit account is " + destAccount.getCurrency());
        }

        // Check the security answer
        boolean correct = checkAnswer(req.securityAnswer(), t.getSecurityAnswerHash());

        // Record the attempt either way
        ETransferAttempt attempt = new ETransferAttempt();
        attempt.setEtransferId(t.getId());
        attempt.setAttemptedByUserId(userId);
        attempt.setCorrect(correct);
        attempt.setIpAddress(ipAddress);
        attempt.setAttemptedAt(Instant.now());
        attemptRepo.save(attempt);

        if (!correct) {
            long wrongCount = attemptRepo.countByEtransferIdAndCorrectFalse(t.getId());
            int remaining = Math.max(0, 3 - (int) wrongCount);

            if (remaining == 0) {
                // 3 strikes: refund the sender, mark LOCKED, audit-clear
                refundToSender(t, "Security answer locked after 3 wrong attempts");
                t.setStatus(ETransferStatus.LOCKED);
                t.setUpdatedAt(Instant.now());
                etransferRepo.save(t);
                log.warn("e-Transfer LOCKED after 3 wrong answers: id={} recipient={}",
                        t.getId(), t.getRecipientEmail());
                throw new SecurityAnswerWrongException(0);
            }

            log.info("e-Transfer wrong answer: id={} attempts={}, remaining={}",
                    t.getId(), wrongCount, remaining);
            throw new SecurityAnswerWrongException(remaining);
        }

        // CORRECT: settle the transfer
        return settleClaim(t, destAccount, userId);
    }
    /**
     * Sender cancels a pending e-transfer. Refunds the sender's account.
     *
     * Only the original sender can cancel. ADMIN/TELLER cancel-on-behalf
     * is a future hardening pass.
     */
    @Transactional
    public ETransfer cancel(Long etransferId, Long userId) {
        ETransfer t = etransferRepo.findByIdForUpdate(etransferId)
                .orElseThrow(() -> new AccessDeniedException("Transfer not found"));

        if (!t.getSenderUserId().equals(userId)) {
            throw new AccessDeniedException("Only the sender can cancel");
        }

        if (t.getStatus() != ETransferStatus.PENDING) {
            throw new IllegalStateException(
                "Can only cancel PENDING transfers; current status: " + t.getStatus());
        }

        refundToSender(t, "Cancelled by sender");
        t.setStatus(ETransferStatus.CANCELLED);
        t.setUpdatedAt(Instant.now());
        ETransfer saved = etransferRepo.save(t);

        log.info("e-Transfer cancelled: id={} userId={}", saved.getId(), userId);
        return saved;
    }

    /** Outgoing transfers for the sender. Used by the dashboard list. */
    @Transactional(readOnly = true)
    public List<ETransfer> listOutgoing(Long userId) {
        return etransferRepo.findBySenderUserIdOrderByCreatedAtDesc(userId);
    }
    /**
     * Register the user's login email for auto-deposit pointed at one of
     * their accounts. Idempotent — if a registration exists for this
     * email globally, only the original owner can update it.
     */
    @Transactional
    public AutoDepositSetting registerAutoDeposit(Long userId, Long targetAccountId) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Verify the target account belongs to this user
        Account target = accountRepo.findById(targetAccountId)
                .orElseThrow(() -> new AccessDeniedException("Account not found"));
        requireOwnership(target, userId);
        requireActive(target);

        String email = user.getEmail().toLowerCase().trim();

        Optional<AutoDepositSetting> existing = autoDepositRepo.findByEmail(email);
        if (existing.isPresent()) {
            AutoDepositSetting s = existing.get();
            if (!s.getUserId().equals(userId)) {
                // Real Interac says: this email is registered at another bank.
                throw new IllegalStateException("This email is already registered for auto-deposit elsewhere");
            }
            s.setTargetAccountId(targetAccountId);
            s.setEnabled(true);
            s.setUpdatedAt(Instant.now());
            return autoDepositRepo.save(s);
        }

        AutoDepositSetting s = new AutoDepositSetting();
        s.setUserId(userId);
        s.setEmail(email);
        s.setTargetAccountId(targetAccountId);
        s.setEnabled(true);
        s.setCreatedAt(Instant.now());
        s.setUpdatedAt(Instant.now());
        AutoDepositSetting saved = autoDepositRepo.save(s);
        log.info("Auto-deposit registered: userId={} email={} target={}", userId, email, targetAccountId);
        return saved;
    }

    @Transactional
    public void disableAutoDeposit(Long userId) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        autoDepositRepo.findByEmail(user.getEmail()).ifPresent(s -> {
            if (!s.getUserId().equals(userId)) {
                throw new AccessDeniedException("Not yours to disable");
            }
            s.setEnabled(false);
            s.setUpdatedAt(Instant.now());
            autoDepositRepo.save(s);
            log.info("Auto-deposit disabled: userId={} email={}", userId, user.getEmail());
        });
    }

    @Transactional(readOnly = true)
    public Optional<AutoDepositSetting> getMyAutoDeposit(Long userId) {
        UserAccount user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return autoDepositRepo.findByEmail(user.getEmail())
                .filter(s -> s.getUserId().equals(userId));
    }

    /**
     * Successful claim: lock Pending + recipient ledger, post settlement
     * journal entry (debit Pending → credit recipient), mark COMPLETED.
     */
    private ETransfer settleClaim(ETransfer t, Account destAccount, Long claimingUserId) {
        LedgerAccount pendingLedger = ledgerAccountRepo.findByCode(PENDING_ETRANSFERS_CODE)
                .orElseThrow(() -> new IllegalStateException("PENDING_ETRANSFERS missing"));
        LedgerAccount destLedger = ledgerAccountRepo.findByBankingAccountId(destAccount.getId())
                .orElseThrow(() -> new IllegalStateException("No ledger account for dest"));

        Money amount = Money.of(t.getAmount(), t.getCurrency());

        // Settlement: Debit Pending (sum increases by +amount) → Credit dest
        // (sum decreases by -amount). Net zero.
        List<PostingRequest> postings = List.of(
                PostingRequest.of(pendingLedger.getId(), amount),         // +amount: Pending releases hold
                PostingRequest.of(destLedger.getId(),    amount.negate()) // -amount: dest LIABILITY increases
        );

        // Idempotency key for the settlement leg: tie it to the etransfer itself.
        // Re-attempts of the same claim won't double-credit.
        String idemKey = "etx-settle-" + t.getId();

        JournalEntry je = ledger.post(
                "e-Transfer claim by " + destAccount.getAccountNumber() + " (etx#" + t.getId() + ")",
                JournalEntryType.ETRANSFER,
                idemKey,
                claimingUserId,
                postings
        );

        t.setStatus(ETransferStatus.COMPLETED);
        t.setRecipientAccountId(destAccount.getId());
        t.setSettlementJournalEntryId(je.getId());
        t.setUpdatedAt(Instant.now());
        ETransfer saved = etransferRepo.save(t);

        log.info("e-Transfer claimed: id={} by user={} into account={} jeId={}",
                saved.getId(), claimingUserId, destAccount.getAccountNumber(), je.getId());
        return saved;
    }

    /**
     * Refund: debit Pending → credit sender's original account.
     * Used by the lockout path here, and the cancel + expiry flows in Part 4.
     *
     * Made package-private so cancel/expiry can call it later.
     */
    void refundToSender(ETransfer t, String reason) {
        LedgerAccount pendingLedger = ledgerAccountRepo.findByCode(PENDING_ETRANSFERS_CODE)
                .orElseThrow(() -> new IllegalStateException("PENDING_ETRANSFERS missing"));
        Account senderAccount = accountRepo.findById(t.getSenderAccountId())
                .orElseThrow(() -> new IllegalStateException("Sender account vanished: " + t.getSenderAccountId()));
        LedgerAccount senderLedger = ledgerAccountRepo.findByBankingAccountId(senderAccount.getId())
                .orElseThrow(() -> new IllegalStateException("No ledger account for sender"));

        Money amount = Money.of(t.getAmount(), t.getCurrency());

        // Pending → Sender: debit Pending (+amount), credit sender (-amount)
        List<PostingRequest> postings = List.of(
                PostingRequest.of(pendingLedger.getId(), amount),
                PostingRequest.of(senderLedger.getId(),  amount.negate())
        );

        String idemKey = "etx-refund-" + t.getId();
        JournalEntry je = ledger.post(
                "e-Transfer refund (etx#" + t.getId() + "): " + reason,
                JournalEntryType.ETRANSFER,
                idemKey,
                t.getSenderUserId(),
                postings
        );

        t.setSettlementJournalEntryId(je.getId());
        log.info("e-Transfer refunded: id={} to senderAccount={} jeId={} reason={}",
                t.getId(), senderAccount.getAccountNumber(), je.getId(), reason);
    }
}