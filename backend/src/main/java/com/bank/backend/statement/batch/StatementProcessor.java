package com.bank.backend.statement.batch;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.ledger.domain.LedgerAccount;
import com.bank.backend.ledger.domain.Posting;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.repository.PostingRepository;
import com.bank.backend.statement.domain.Statement;
import com.bank.backend.statement.service.StatementData;
import com.bank.backend.statement.service.StatementPdfRenderer;
import com.bank.backend.statement.service.StatementStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Processor: takes ONE Account, looks up the active period from the
 * BatchJobContext (set by the launcher just before the job started),
 * computes the statement data, renders the PDF, writes it to disk, and
 * returns a Statement entity ready for the writer to persist.
 *
 * Reader → Processor → Writer is the standard Spring Batch triad.
 */
public class StatementProcessor implements ItemProcessor<Account, Statement> {

    private static final Logger log = LoggerFactory.getLogger(StatementProcessor.class);

    private final BatchJobContext context;
    private final AccountHolderRepository holderRepo;
    private final LedgerAccountRepository ledgerAccountRepo;
    private final PostingRepository postingRepo;
    private final StatementPdfRenderer renderer;
    private final StatementStorage storage;

    public StatementProcessor(
            BatchJobContext context,
            AccountHolderRepository holderRepo,
            LedgerAccountRepository ledgerAccountRepo,
            PostingRepository postingRepo,
            StatementPdfRenderer renderer,
            StatementStorage storage
    ) {
        this.context = context;
        this.holderRepo = holderRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.postingRepo = postingRepo;
        this.renderer = renderer;
        this.storage = storage;
    }

    @Override
    public Statement process(Account account) {
        // Read the active period at the start of each call. The launcher
        // sets this on the shared BatchJobContext just before invoking
        // the Spring Batch job.
        YearMonth period = context.period();

        // Find the corresponding ledger account
        LedgerAccount ledgerAcct = ledgerAccountRepo.findByBankingAccountId(account.getId())
                .orElse(null);
        if (ledgerAcct == null) {
            log.warn("Account {} has no ledger account; skipping", account.getAccountNumber());
            return null;
        }

        // Period bounds in the system zone, then converted to instants
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEndInclusive = period.atEndOfMonth();
        Instant periodStartInstant = periodStart
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant periodEndExclusive = periodEndInclusive.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();

        // All postings against this ledger account, ordered by time ASC
        List<Posting> all = postingRepo.findByLedgerAccountIdOrderByCreatedAtDesc(ledgerAcct.getId());
        all.sort(Comparator.comparing(Posting::getCreatedAt));

        // Compute opening balance: sum of postings strictly before the period.
        // For LIABILITY deposit accounts the customer-facing balance = -SUM(amount).
        BigDecimal openingRaw = BigDecimal.ZERO;
        for (Posting p : all) {
            if (p.getCreatedAt().isBefore(periodStartInstant)) {
                openingRaw = openingRaw.add(p.getAmount());
            }
        }
        BigDecimal openingBalance = openingRaw.negate();

        // Build the line items: one per in-period posting, with running balance.
        List<StatementData.Line> lines = new ArrayList<>();
        BigDecimal runningRaw = openingRaw;
        for (Posting p : all) {
            if (p.getCreatedAt().isBefore(periodStartInstant)) continue;
            if (!p.getCreatedAt().isBefore(periodEndExclusive)) break;

            runningRaw = runningRaw.add(p.getAmount());
            BigDecimal customerSigned = p.getAmount().negate();
            BigDecimal runningBalance = runningRaw.negate();

            lines.add(new StatementData.Line(
                    p.getCreatedAt(),
                    descriptionFor(p),
                    customerSigned,
                    runningBalance
            ));
        }

        BigDecimal closingBalance = runningRaw.negate();

        // Build the renderer input
        String customerName = primaryHolderName(account);
        StatementData data = new StatementData(
                customerName,
                account.getAccountNumber(),
                accountTypeLabel(account),
                account.getCurrency(),
                periodStart,
                periodEndInclusive,
                openingBalance,
                closingBalance,
                lines
        );

        // Render and store
        byte[] bytes = renderer.render(data);
        Path path = storage.pathFor(account.getAccountNumber(), period.getYear(), period.getMonthValue());
        storage.write(path, bytes);

        // Build the Statement entity for the writer to persist
        Statement stmt = new Statement();
        stmt.setAccountId(account.getId());
        stmt.setPeriodYear(period.getYear());
        stmt.setPeriodMonth(period.getMonthValue());
        stmt.setFilePath(path.toString());
        stmt.setFileSizeBytes((long) bytes.length);
        stmt.setTransactionCount(lines.size());
        stmt.setOpeningBalance(openingBalance);
        stmt.setClosingBalance(closingBalance);
        stmt.setCurrency(account.getCurrency());
        stmt.setGeneratedAt(Instant.now());

        log.info("Generated statement: account={} period={} txns={} bytes={}",
                account.getAccountNumber(), period, lines.size(), bytes.length);

        return stmt;
    }

    private String primaryHolderName(Account account) {
        return holderRepo.findByAccount(account).stream()
                .filter(h -> h.getRemovedAt() == null)
                .findFirst()
                .map(this::holderName)
                .orElse("Account Holder");
    }

    private String holderName(AccountHolder h) {
        var c = h.getCustomer();
        if (c == null) return "Account Holder";
        String first = c.getLegalFirstName();
        String last = c.getLegalLastName();
        return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
    }

    private String accountTypeLabel(Account a) {
        return switch (a.getAccountType()) {
            case CHEQUING -> "Chequing";
            case SAVINGS -> "Savings";
            case TFSA -> "TFSA";
        };
    }

    private String descriptionFor(Posting p) {
        // Per-posting description doesn't exist in our schema; the journal_entry
        // carries it. For now we use a fixed string per posting; the batch could
        // be enhanced to JOIN with journal_entries for richer text.
        if (p.getAmount().signum() < 0) return "Credit (incoming)";
        return "Debit (outgoing)";
    }
}