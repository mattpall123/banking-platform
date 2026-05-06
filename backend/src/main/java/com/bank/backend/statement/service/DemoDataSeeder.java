package com.bank.backend.statement.service;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.account.service.AccountService;
import com.bank.backend.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates plausible-looking transactions for demo purposes.
 *
 * Calling seedRealistic(account, n) creates n deposit/withdraw entries
 * with varied descriptions and amounts. Useful for filling out a month
 * of statements when you don't have real activity to show.
 *
 * NOT a fixture loader (we don't seed at boot) — only fires when an admin
 * explicitly calls it.
 */
@Service
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final List<String> CREDIT_DESCRIPTIONS = List.of(
            "Payroll deposit",
            "Interac e-Transfer in",
            "Refund",
            "GST credit",
            "Interest payment"
    );

    private static final List<String> DEBIT_DESCRIPTIONS = List.of(
            "Coffee shop",
            "Grocery store",
            "Hydro bill",
            "Internet bill",
            "Restaurant",
            "Streaming subscription",
            "Pharmacy",
            "Gas station",
            "Phone bill",
            "Public transit"
    );

    private final AccountRepository accountRepo;
    private final AccountService accountService;
    private final Random random = new Random();

    public DemoDataSeeder(AccountRepository accountRepo, AccountService accountService) {
        this.accountRepo = accountRepo;
        this.accountService = accountService;
    }

    /**
     * Fire {count} mixed transactions on the given account. Roughly 60% are
     * deposits (credits) and 40% withdrawals (debits) so balances trend
     * upward and we don't accidentally overdraft.
     *
     * NOTE: these go through accountService.deposit/withdraw which post real
     * journal entries with `now()` timestamps. They will appear in the CURRENT
     * month's statement. (Backdating would require new ledger plumbing.)
     */
    @Transactional
    public int seedRealistic(Long accountId, int count, Long actingUserId) {
        Account account = accountRepo.findById(accountId).orElseThrow();
        int posted = 0;

        for (int i = 0; i < count; i++) {
            boolean credit = random.nextDouble() < 0.6;
            String description = pick(credit ? CREDIT_DESCRIPTIONS : DEBIT_DESCRIPTIONS);
            BigDecimal amount = randomAmount(credit);

            String idempotencyKey = "seed-" + UUID.randomUUID();
            Money money = Money.of(amount, account.getCurrency());

            try {
                if (credit) {
                    accountService.deposit(account, money, idempotencyKey, actingUserId);
                } else {
                    accountService.withdraw(account, money, idempotencyKey, actingUserId);
                }
                posted++;
                log.debug("Seeded {} {} on account {} ({})",
                        credit ? "credit" : "debit", money, account.getAccountNumber(), description);
            } catch (RuntimeException ex) {
                // Most likely "Insufficient funds" on a withdraw. Skip silently.
                log.debug("Skipped seed entry: {}", ex.getMessage());
            }
        }

        log.info("Seed run: posted {} of {} requested entries on account {}",
                posted, count, account.getAccountNumber());
        return posted;
    }

    private String pick(List<String> options) {
        return options.get(random.nextInt(options.size()));
    }

    /** Credits: $50–$2000. Debits: $5–$200. Realistic-ish ranges. */
    private BigDecimal randomAmount(boolean credit) {
        double min = credit ? 50.0 : 5.0;
        double max = credit ? 2000.0 : 200.0;
        double v = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(Math.round(v * 100.0) / 100.0);
    }
}