package com.bank.backend.account.web;

import com.bank.backend.account.dto.TransferRequest;
import com.bank.backend.account.service.TransferService;
import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.ledger.domain.JournalEntry;
import com.bank.backend.ledger.dto.JournalEntryResponse;
import com.bank.backend.shared.Money;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.bank.backend.audit.service.Audited;
import static com.bank.backend.audit.domain.AuditAction.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Audited(action = TRANSFER_EXECUTE, resourceType = "Transfer")
    public ResponseEntity<JournalEntryResponse> transfer(
            @Valid @RequestBody TransferRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CurrentUser user
    ) {
        Money amount = Money.of(body.amount(), body.currency());

        JournalEntry je = transferService.transfer(
                body.sourceAccountId(),
                body.destinationAccountNumber(),
                amount,
                user.userId(),
                idempotencyKey
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(JournalEntryResponse.from(je));
    }
}