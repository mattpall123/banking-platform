package com.bank.backend.audit.web;

import com.bank.backend.audit.dto.AuditEventResponse;
import com.bank.backend.audit.repository.AuditEventRepository;
import com.bank.backend.audit.service.AuditService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Audit-log read endpoints.
 *
 * AUTHZ NOTE: should be admin-only. Until Session 9 introduces RBAC, every
 * authenticated user can read the audit log. Acceptable for development;
 * the @PreAuthorize("hasRole('ADMIN')") annotation is the eventual fix.
 */


@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditEventRepository repo;
    private final AuditService auditService;

    public AuditController(AuditEventRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    /**
     * List the most recent N audit events. Newest first.
     * Default 50; max 200.
     */
    @GetMapping
    public ResponseEntity<List<AuditEventResponse>> list(
            @RequestParam(defaultValue = "50") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        var page = repo.findAllByOrderByIdDesc(PageRequest.of(0, safeLimit));
        return ResponseEntity.ok(page.map(AuditEventResponse::from).getContent());
    }

    /**
     * Verify the integrity of the audit chain. Returns the id of the first
     * tampered row (if any). For our scale we walk the entire log, but this
     * is intentionally not paginated — verification has to be all-or-nothing.
     */
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify() {
        Optional<Long> firstBadId = auditService.verifyChain();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("intact", firstBadId.isEmpty());
        body.put("firstTamperedId", firstBadId.orElse(null));
        return ResponseEntity.ok(body);
    }
}