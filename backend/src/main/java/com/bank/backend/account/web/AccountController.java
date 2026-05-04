package com.bank.backend.account.web;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.dto.AccountResponse;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.account.service.AccountService;
import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.dto.JournalEntryResponse;
import com.bank.backend.ledger.dto.MoneyRequest;
import com.bank.backend.shared.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepo;
    private final AccountHolderRepository accountHolderRepo;
    private final CustomerRepository customerRepo;
    private final AccountService accountService;

    public AccountController(
            AccountRepository accountRepo,
            AccountHolderRepository accountHolderRepo,
            CustomerRepository customerRepo,
            AccountService accountService
    ) {
        this.accountRepo = accountRepo;
        this.accountHolderRepo = accountHolderRepo;
        this.customerRepo = customerRepo;
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public ResponseEntity<List<AccountResponse>> listMyAccounts(
            @AuthenticationPrincipal CurrentUser user
    ) {
        Customer customer = customerRepo.findByUserId(user.userId())
                .orElseThrow(() -> new IllegalStateException(
                    "No customer record for authenticated user"));

        List<AccountHolder> holdings = accountHolderRepo.findByCustomer(customer);

        List<AccountResponse> response = holdings.stream()
                .filter(h -> h.getRemovedAt() == null)
                .map(AccountHolder::getAccount)
                .map(account -> AccountResponse.of(
                        account,
                        accountService.getBalance(account).displayAmount()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<JournalEntryResponse> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CurrentUser user
    ) {
        Account account = loadOwnedAccount(accountId, user);
        Money amount = Money.of(body.amount(), body.currency());

        JournalEntry je = accountService.deposit(account, amount, idempotencyKey, user.userId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JournalEntryResponse.from(je));
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<JournalEntryResponse> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CurrentUser user
    ) {
        Account account = loadOwnedAccount(accountId, user);
        Money amount = Money.of(body.amount(), body.currency());

        JournalEntry je = accountService.withdraw(account, amount, idempotencyKey, user.userId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JournalEntryResponse.from(je));
    }

    private Account loadOwnedAccount(Long accountId, CurrentUser user) {
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

        return account;
    }
}
