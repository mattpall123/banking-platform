package com.bank.backend.audit.dto;

import com.bank.backend.audit.domain.AuditEvent;
import com.bank.backend.audit.domain.AuditOutcome;

import java.time.Instant;

public record AuditEventResponse(
    Long id,
    Long userId,
    String actorEmail,
    String action,
    String resourceType,
    String resourceId,
    AuditOutcome outcome,
    String failureReason,
    String ipAddress,
    String userAgent,
    String metadataJson,
    Instant occurredAt,
    String prevHash,
    String rowHash
) {
    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(), e.getUserId(), e.getActorEmail(),
                e.getAction(), e.getResourceType(), e.getResourceId(),
                e.getOutcome(), e.getFailureReason(),
                e.getIpAddress(), e.getUserAgent(), e.getMetadataJson(),
                e.getOccurredAt(), e.getPrevHash(), e.getRowHash()
        );
    }
}