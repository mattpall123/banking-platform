package com.bank.backend.account.service;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountStatus;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.domain.JournalEntryType;
import com.bank.backend.ledger.domain.LedgerAccount;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.service.LedgerService;
import com.bank.backend.ledger.service.PostingRequest;
import com.bank.backend.shared.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bank.backend.shared.MetricsService;
import java.math.BigDecimal;
import java.util.List;

/**
 * Account-facing operations: deposit, withdraw, get balance.
 *
 * Money flow for a deposit (Alice puts $50 cash into chequing):
 *   DEBIT  Cash             +50  (bank's cash on hand goes up)
 *   CREDIT Alice's deposit  +50  (bank's liability to Alice goes up)
 *                                 stored as +50 because for LIABILITY
 *                                 accounts, +amount = balance up
 *
 *   Wait — both +50? That doesn't sum to zero!
 *
 * The SIGN CONVENTION fixes this. We keep zero-sum by storing one side
 * as positive and the other as negative. Concretely we'll do:
 *   Cash:                 +50
 *   Customer Deposit:     -50    (negative posting on the liability;
 *                                 to read the deposit balance, query SUM
 *                                 and negate, OR store all liability
 *                                 amounts negatively)
 *
 * Easier mental model: "The thing that grows the source has +X; the thing
 * that grows the destination has -X." But to display Alice's positive
 * balance, we just negate the SUM when reading her deposit account
 * (because deposits are LIABILITY accounts that go up via negative
 * postings in our convention).
 *
 * Actually let's stop and pick ONE convention and stick to it. The cleanest:
 *
 *   For ASSET / EXPENSE:    balance = +SUM(amount)
 *   For LIABILITY / EQUITY / REVENUE: balance = -SUM(amount)
 *
 * Then for a deposit:
 *   Cash             +50  (asset goes up by +50 → balance +50 ✓)
 *   Customer Deposit -50  (liability balance is -SUM = -(-50) = +50 ✓)
 *   Sum:               0  ✓
 *
 * For Alice's $20 withdrawal:
 *   Cash             -20  (asset goes down by 20 → -20 added to running sum)
 *   Customer Deposit +20  (liability "negated balance" goes from +50 to
 *                          -SUM = -(-50+20) = -(-30) = +30 ✓)
 *   Sum:               0  ✓
 */
@Service
public class AccountService {

    private final AccountRepository accountRepo;
    private final LedgerAccountRepository ledgerAccountRepo;
    private final LedgerService ledger;

    private final MetricsService metrics;

    public AccountService(
            AccountRepository accountRepo,
            LedgerAccountRepository ledgerAccountRepo,
            LedgerService ledger,
            MetricsService metrics
    ) {
        this.accountRepo = accountRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.ledger = ledger;
        this.metrics = metrics;
    }

    /**
     * Returns the balance of a banking account, computed from the ledger.
     * Customer deposit accounts are LIABILITY in our chart, so we negate
     * the raw posting sum to give a positive customer-facing balance.
     */
    public Money getBalance(Account account) {
        LedgerAccount ledgerAcct = ledgerAccountRepo.findByBankingAccountId(account.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "No ledger account for banking account " + account.getId()));

        BigDecimal rawSum = ledger.balanceOf(ledgerAcct.getId());
        // LIABILITY: negate so customer sees a positive balance
        return Money.of(rawSum.negate(), account.getCurrency());
    }

    @Transactional
    public JournalEntry deposit(Account account, Money amount, String idempotencyKey, Long userId) {
        requireActive(account);
        requireSameCurrency(account, amount);
        requirePositive(amount);

        Long cashAccountId = ledgerAccountRepo.findByCode("CASH")
                .orElseThrow(() -> new IllegalStateException("CASH ledger account missing"))
                .getId();
        Long depositAccountId = ledgerAccountRepo.findByBankingAccountId(account.getId())
                .orElseThrow(() -> new IllegalStateException("No deposit ledger account"))
                .getId();

        // Cash (asset): +amount, Customer Deposit (liability): -amount
        List<PostingRequest> postings = List.of(
            PostingRequest.of(cashAccountId,    amount),
            PostingRequest.of(depositAccountId, amount.negate())
        );
        metrics.deposit();
        return ledger.post(
            "Deposit to " + account.getAccountNumber(),
            JournalEntryType.DEPOSIT,
            idempotencyKey,
            userId,
            postings
        );
    }

    @Transactional
    public JournalEntry withdraw(Account account, Money amount, String idempotencyKey, Long userId) {
        requireActive(account);
        requireSameCurrency(account, amount);
        requirePositive(amount);

        Money currentBalance = getBalance(account);
        if (currentBalance.isLessThan(amount)) {
            throw new IllegalArgumentException(
                "Insufficient funds: balance " + currentBalance + ", requested " + amount);
        }

        Long cashAccountId = ledgerAccountRepo.findByCode("CASH").orElseThrow().getId();
        Long depositAccountId = ledgerAccountRepo.findByBankingAccountId(account.getId())
                .orElseThrow().getId();

        // Cash (asset) goes down, Customer Deposit (liability) goes down
        List<PostingRequest> postings = List.of(
            PostingRequest.of(cashAccountId,    amount.negate()),
            PostingRequest.of(depositAccountId, amount)
        );
        metrics.withdrawal();
        return ledger.post(
            "Withdrawal from " + account.getAccountNumber(),
            JournalEntryType.WITHDRAWAL,
            idempotencyKey,
            userId,
            postings
        );
    }

    // ---- guards ----

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active: " + account.getStatus());
        }
    }

    private void requireSameCurrency(Account account, Money amount) {
        if (!account.getCurrency().equals(amount.currencyCode())) {
            throw new IllegalArgumentException(
                "Currency mismatch: account is " + account.getCurrency() +
                ", amount is " + amount.currencyCode());
        }
    }

    private void requirePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}