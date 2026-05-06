package com.bank.backend.statement.web;

import com.bank.backend.account.domain.Account;
import com.bank.backend.account.domain.AccountHolder;
import com.bank.backend.account.repository.AccountHolderRepository;
import com.bank.backend.account.repository.AccountRepository;
import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.customer.domain.Customer;
import com.bank.backend.customer.repository.CustomerRepository;
import com.bank.backend.statement.domain.Statement;
import com.bank.backend.statement.dto.StatementResponse;
import com.bank.backend.statement.repository.StatementRepository;
import com.bank.backend.statement.service.StatementStorage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

/**
 * Customer-facing statement endpoints:
 *   GET /api/statements/me           - list my statements
 *   GET /api/statements/{id}/download - download one PDF (ownership-checked)
 */
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementRepository statementRepo;
    private final AccountRepository accountRepo;
    private final AccountHolderRepository holderRepo;
    private final CustomerRepository customerRepo;
    private final StatementStorage storage;

    public StatementController(
            StatementRepository statementRepo,
            AccountRepository accountRepo,
            AccountHolderRepository holderRepo,
            CustomerRepository customerRepo,
            StatementStorage storage
    ) {
        this.statementRepo = statementRepo;
        this.accountRepo = accountRepo;
        this.holderRepo = holderRepo;
        this.customerRepo = customerRepo;
        this.storage = storage;
    }

    /** Statements for any account the authenticated user holds. Newest first. */
    @GetMapping("/me")
    public ResponseEntity<List<StatementResponse>> myStatements(
            @AuthenticationPrincipal CurrentUser user
    ) {
        Customer customer = customerRepo.findByUserId(user.userId())
                .orElseThrow(() -> new IllegalStateException("No customer for user"));

        List<Long> accountIds = holderRepo.findByCustomer(customer).stream()
                .filter(h -> h.getRemovedAt() == null)
                .map(AccountHolder::getAccount)
                .map(Account::getId)
                .toList();

        if (accountIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<StatementResponse> body = statementRepo
                .findByAccountIdInOrderByPeriodYearDescPeriodMonthDesc(accountIds)
                .stream()
                .map(StatementResponse::from)
                .toList();

        return ResponseEntity.ok(body);
    }

    /**
     * Download one statement's PDF. Ownership check identical to other
     * account-scoped endpoints (loadOwnedAccount pattern).
     */
    @GetMapping("/{statementId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long statementId,
            @AuthenticationPrincipal CurrentUser user
    ) {
        Statement stmt = statementRepo.findById(statementId)
                .orElseThrow(() -> new AccessDeniedException("Statement not found or not yours"));

        // Ownership check
        Account account = accountRepo.findById(stmt.getAccountId())
                .orElseThrow(() -> new AccessDeniedException("Statement not found or not yours"));

        Customer customer = customerRepo.findByUserId(user.userId())
                .orElseThrow(() -> new AccessDeniedException("No customer for user"));

        boolean isHolder = holderRepo.findByAccount(account).stream()
                .anyMatch(h -> h.getRemovedAt() == null
                        && h.getCustomer().getId().equals(customer.getId()));
        if (!isHolder) {
            throw new AccessDeniedException("Statement not found or not yours");
        }

        byte[] bytes = storage.read(Path.of(stmt.getFilePath()));

        String filename = String.format("statement-%s-%04d-%02d.pdf",
                account.getAccountNumber(),
                stmt.getPeriodYear(),
                stmt.getPeriodMonth());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header("X-Filename", filename)   // workaround so the browser-side fetch can read it
                .body(bytes);
    }
}