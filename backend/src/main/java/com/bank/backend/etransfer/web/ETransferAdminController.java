package com.bank.backend.etransfer.web;

import com.bank.backend.etransfer.service.ETransferExpiryExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for e-transfers. Currently just lets ADMIN force the
 * expiry runner without waiting for the cron tick — useful for demos.
 */
@RestController
@RequestMapping("/api/admin/etransfers")
@PreAuthorize("hasRole('ADMIN')")
public class ETransferAdminController {

    private final ETransferExpiryExecutor executor;

    public ETransferAdminController(ETransferExpiryExecutor executor) {
        this.executor = executor;
    }

    @PostMapping("/run-expiry")
    public ResponseEntity<Map<String, Object>> forceExpiry() {
        var due = executor.findDueForExpiry(Instant.now());
        int succeeded = 0;
        int failed = 0;
        for (var t : due) {
            try {
                executor.expireOne(t);
                succeeded++;
            } catch (Exception e) {
                failed++;
            }
        }
        return ResponseEntity.ok(Map.of(
                "found", due.size(),
                "expired", succeeded,
                "failed", failed
        ));
    }
}