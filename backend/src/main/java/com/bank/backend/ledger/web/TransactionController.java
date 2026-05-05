package com.bank.backend.ledger.web;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.domain.LedgerAccount;
import com.bank.backend.ledger.domain.Posting;
import com.bank.backend.ledger.dto.TransactionResponse;
import com.bank.backend.ledger.repository.LedgerAccountRepository;
import com.bank.backend.ledger.repository.PostingRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Transaction history. Reads postings against a customer's ledger account
 * and presents them as customer-friendly signed amounts (+ for money in,
 * - for money out).
 *
 * Liability sign-convention reminder:
 *   raw posting -100 on a deposit account = +100 to customer (deposit)
 *   raw posting +50  on a deposit account = -50  to customer (withdrawal)
 * So we negate the raw amount on the way out.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final AccountRepository accountRepo;
    private final AccountHolderRepository accountHolderRepo;
    private final CustomerRepository customerRepo;
    private final LedgerAccountRepository ledgerAccountRepo;
    private final PostingRepository postingRepo;

    public TransactionController(
            AccountRepository accountRepo,
            AccountHolderRepository accountHolderRepo,
            CustomerRepository customerRepo,
            LedgerAccountRepository ledgerAccountRepo,
            PostingRepository postingRepo
    ) {
        this.accountRepo = accountRepo;
        this.accountHolderRepo = accountHolderRepo;
        this.customerRepo = customerRepo;
        this.ledgerAccountRepo = ledgerAccountRepo;
        this.postingRepo = postingRepo;
    }

    @GetMapping("/me")
    public ResponseEntity<List<TransactionResponse>> myTransactions(
            @RequestParam Long accountId,
            @AuthenticationPrincipal CurrentUser user
    ) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new AccessDeniedException("Account not found or not yours"));

        Customer customer = customerRepo.findByUserId(user.userId())
                .orElseThrow(() -> new AccessDeniedException("No customer for user"));

        boolean isHolder = accountHolderRepo.findByAccount(account).stream()
                .anyMatch(h -> h.getRemovedAt() == null
                        && h.getCustomer().getId().equals(customer.getId()));
        if (!isHolder) {
            throw new AccessDeniedException("Account not found or not yours");
        }

        LedgerAccount ledgerAcct = ledgerAccountRepo.findByBankingAccountId(account.getId())
                .orElseThrow(() -> new IllegalStateException(
                    "No ledger account for banking account " + account.getId()));

        List<Object[]> rows = postingRepo.findHistoryForLedgerAccount(ledgerAcct.getId());

        List<TransactionResponse> response = rows.stream()
                .map(row -> {
                    Posting p = (Posting) row[0];
                    JournalEntry je = (JournalEntry) row[1];
                    return new TransactionResponse(
                            je.getId(),
                            je.getDescription(),
                            je.getEntryType(),
                            // Liability sign-flip: customer-facing amount is the negation
                            p.getAmount().negate(),
                            p.getCurrency(),
                            je.getOccurredAt()
                    );
                })
                .toList();

        return ResponseEntity.ok(response);
    }
}