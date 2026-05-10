package com.bank.backend.etransfer.web;

import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.etransfer.domain.ETransfer;
import com.bank.backend.etransfer.dto.ETransferResponse;
import com.bank.backend.etransfer.dto.SendETransferRequest;
import com.bank.backend.etransfer.service.ETransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/etransfers")
public class ETransferController {

    private final ETransferService service;

    public ETransferController(ETransferService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<ETransferResponse> send(
            @Valid @RequestBody SendETransferRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CurrentUser user
    ) {
        ETransfer t = service.send(req, user.userId(), idempotencyKey);
        return ResponseEntity.ok(ETransferResponse.from(t));
    }

    @GetMapping("/incoming")
    public ResponseEntity<java.util.List<com.bank.backend.etransfer.dto.IncomingETransferResponse>> incoming(
            @AuthenticationPrincipal CurrentUser user
    ) {
        return ResponseEntity.ok(service.listIncoming(user.userId()));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ETransferResponse> claim(
            @PathVariable Long id,
            @Valid @RequestBody com.bank.backend.etransfer.dto.ClaimETransferRequest req,
            @AuthenticationPrincipal CurrentUser user,
            jakarta.servlet.http.HttpServletRequest httpReq
    ) {
        ETransfer t = service.claim(id, req, user.userId(), httpReq.getRemoteAddr());
        return ResponseEntity.ok(ETransferResponse.from(t));
    }

    @GetMapping("/outgoing")
    public ResponseEntity<java.util.List<ETransferResponse>> outgoing(
            @AuthenticationPrincipal CurrentUser user
    ) {
        java.util.List<ETransferResponse> body = service.listOutgoing(user.userId()).stream()
                .map(ETransferResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ETransferResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        ETransfer t = service.cancel(id, user.userId());
        return ResponseEntity.ok(ETransferResponse.from(t));
    }

    @GetMapping("/auto-deposit")
    public ResponseEntity<com.bank.backend.etransfer.dto.AutoDepositResponse> getAutoDeposit(
            @AuthenticationPrincipal CurrentUser user
    ) {
        return service.getMyAutoDeposit(user.userId())
                .map(s -> ResponseEntity.ok(com.bank.backend.etransfer.dto.AutoDepositResponse.from(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/auto-deposit")
    public ResponseEntity<com.bank.backend.etransfer.dto.AutoDepositResponse> setAutoDeposit(
            @Valid @RequestBody com.bank.backend.etransfer.dto.AutoDepositRequest req,
            @AuthenticationPrincipal CurrentUser user
    ) {
        var saved = service.registerAutoDeposit(user.userId(), req.targetAccountId());
        return ResponseEntity.ok(com.bank.backend.etransfer.dto.AutoDepositResponse.from(saved));
    }

    @DeleteMapping("/auto-deposit")
    public ResponseEntity<Void> deleteAutoDeposit(
            @AuthenticationPrincipal CurrentUser user
    ) {
        service.disableAutoDeposit(user.userId());
        return ResponseEntity.noContent().build();
    }
}