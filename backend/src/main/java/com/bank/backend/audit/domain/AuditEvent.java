package com.bank.backend.audit.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable record of one auditable action.
 *
 * NEVER updated after insert. Corrections happen by appending a new event
 * with action="audit.correction" and metadata referencing the original.
 *
 * The hash chain is enforced at insert time by AuditService — JPA does NOT
 * compute hashes automatically.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "prev_hash", length = 64)
    private String prevHash;

    @Column(name = "row_hash", nullable = false, length = 64)
    private String rowHash;
}