package com.bank.backend.audit.service;

import com.bank.backend.audit.domain.AuditEvent;
import com.bank.backend.audit.domain.AuditOutcome;
import com.bank.backend.audit.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Persists audit events with a SHA-256 hash chain for tamper evidence.
 *
 * Each row's row_hash = SHA-256(canonical(this row's fields) + prev_hash).
 *
 * CANONICALIZATION: we serialise the row's fields into a deterministic
 * string. Field order is fixed; null fields are written as the literal
 * "null" so the hash distinguishes "field absent" from any value.
 * Maps in metadata_json are kept lexicographically sorted via TreeMap so
 * the JSON serialisation is deterministic.
 *
 * VERIFICATION: a separate verify() method re-walks the chain and reports
 * the index of any row whose computed hash differs from its stored
 * row_hash, which would indicate tampering.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
        // A dedicated ObjectMapper so we control serialisation behaviour and
        // don't get surprised by app-wide config changes later.
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Append a single audit event. Runs in REQUIRES_NEW so a failed audit
     * write doesn't taint the caller's transaction... wait, actually we
     * want the OPPOSITE. We want the audit row to commit ONLY if the
     * business operation commits, and we want a failed audit insert to
     * roll back the business op. So: REQUIRED (default) — we share the
     * caller's transaction.
     *
     * If the caller has no transaction, a new one is created for just
     * the audit insert.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(
            Long userId,
            String actorEmail,
            String action,
            String resourceType,
            String resourceId,
            AuditOutcome outcome,
            String failureReason,
            String ipAddress,
            String userAgent,
            Map<String, ?> metadata
    ) {
        AuditEvent event = new AuditEvent();
        event.setUserId(userId);
        event.setActorEmail(actorEmail);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setResourceId(resourceId);
        event.setOutcome(outcome);
        event.setFailureReason(failureReason);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setMetadataJson(serialiseMetadata(metadata));
        event.setOccurredAt(Instant.now());

        // Chain to the previous row's hash.
        Optional<AuditEvent> prev = repo.findMostRecent();
        String prevHash = prev.map(AuditEvent::getRowHash).orElse(null);
        event.setPrevHash(prevHash);

        // Compute this row's hash from its canonical form + prev_hash.
        String canonical = canonicalForm(event);
        String rowHash = sha256Hex(canonical + (prevHash == null ? "" : prevHash));
        event.setRowHash(rowHash);

        return repo.save(event);
    }

    /**
     * Walk the entire chain from oldest to newest. Returns the id of the
     * first row whose stored hash doesn't match its recomputed hash, or
     * empty if the chain is intact.
     */
    @Transactional(readOnly = true)
    public Optional<Long> verifyChain() {
        // For a small audit log this fits in memory. For a real system you'd
        // page through (audit logs grow unbounded; this is a known
        // limitation we accept at our scale).
        var all = repo.findAll();
        all.sort((a, b) -> Long.compare(a.getId(), b.getId()));

        String expectedPrev = null;
        for (AuditEvent e : all) {
            // prev_hash should equal the previous row's row_hash
            if (!java.util.Objects.equals(e.getPrevHash(), expectedPrev)) {
                log.warn("Audit chain break at id={}: prev_hash mismatch", e.getId());
                return Optional.of(e.getId());
            }
            String recomputed = sha256Hex(canonicalForm(e) + (expectedPrev == null ? "" : expectedPrev));
            if (!recomputed.equals(e.getRowHash())) {
                log.warn("Audit chain break at id={}: row_hash mismatch", e.getId());
                return Optional.of(e.getId());
            }
            expectedPrev = e.getRowHash();
        }
        return Optional.empty();
    }

    // ---- canonicalisation ----

    /**
     * Deterministic string serialisation of an audit event's fields, used as
     * input to SHA-256. Field order is FIXED; changing it would break the
     * chain for ALL historical rows. Pipe-separated; "null" sentinel for
     * missing values.
     */
    private static String canonicalForm(AuditEvent e) {
        return String.join("|",
                ns(e.getUserId()),
                ns(e.getActorEmail()),
                ns(e.getAction()),
                ns(e.getResourceType()),
                ns(e.getResourceId()),
                ns(e.getOutcome()),
                ns(e.getFailureReason()),
                ns(e.getIpAddress()),
                ns(e.getUserAgent()),
                ns(e.getMetadataJson()),
                ns(e.getOccurredAt())
        );
    }

    /** Null-safe stringifier; returns the literal "null" for missing values. */
    private static String ns(Object v) {
        return v == null ? "null" : v.toString();
    }

    private String serialiseMetadata(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            // TreeMap → keys serialised in sorted order, deterministic JSON.
            return objectMapper.writeValueAsString(new TreeMap<>(metadata));
        } catch (JsonProcessingException ex) {
            // Fallback rather than fail the whole audit write.
            log.warn("Failed to serialise audit metadata, falling back to toString(): {}", ex.getMessage());
            return new TreeMap<>(metadata).toString();
        }
    }

    // ---- crypto ----

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is required by every JVM. If this throws, the JVM is broken.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}