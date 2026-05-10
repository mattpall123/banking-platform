package com.bank.backend.etransfer.dto;

import com.bank.backend.etransfer.domain.ETransfer;
import com.bank.backend.etransfer.domain.ETransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response shape for sender + recipient views.
 *
 * NEVER includes securityAnswerHash. The hash exists only for
 * server-side comparison; exposing it would let a thief offline-crack it.
 */
public record ETransferResponse(
    Long id,
    Long senderAccountId,
    String recipientEmail,
    String recipientName,
    Long recipientAccountId,
    BigDecimal amount,
    String currency,
    String message,
    String securityQuestion,    // shown to recipient at claim time
    Boolean autoDeposited,
    ETransferStatus status,
    Instant expiresAt,
    Instant createdAt
) {
    public static ETransferResponse from(ETransfer e) {
        return new ETransferResponse(
            e.getId(),
            e.getSenderAccountId(),
            e.getRecipientEmail(),
            e.getRecipientName(),
            e.getRecipientAccountId(),
            e.getAmount(),
            e.getCurrency(),
            e.getMessage(),
            e.getSecurityQuestion(),
            e.getAutoDeposited(),
            e.getStatus(),
            e.getExpiresAt(),
            e.getCreatedAt()
        );
    }
}