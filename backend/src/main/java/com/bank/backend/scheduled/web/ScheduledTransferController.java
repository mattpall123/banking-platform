package com.bank.backend.scheduled.web;

import com.bank.backend.auth.service.CurrentUser;
import com.bank.backend.scheduled.domain.ScheduledTransfer;
import com.bank.backend.scheduled.dto.CreateScheduledTransferRequest;
import com.bank.backend.scheduled.dto.ScheduledTransferResponse;
import com.bank.backend.scheduled.service.ScheduledTransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduled-transfers")
public class ScheduledTransferController {

    private final ScheduledTransferService service;

    public ScheduledTransferController(ScheduledTransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ScheduledTransferResponse> create(
            @Valid @RequestBody CreateScheduledTransferRequest req,
            @AuthenticationPrincipal CurrentUser user
    ) {
        ScheduledTransfer st = service.create(req, user.userId());
        return ResponseEntity.ok(ScheduledTransferResponse.from(st));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ScheduledTransferResponse>> listMine(
            @AuthenticationPrincipal CurrentUser user
    ) {
        List<ScheduledTransferResponse> body = service.listForUser(user.userId()).stream()
                .map(ScheduledTransferResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ScheduledTransferResponse> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        ScheduledTransfer st = service.cancel(id, user.userId());
        return ResponseEntity.ok(ScheduledTransferResponse.from(st));
    }
}