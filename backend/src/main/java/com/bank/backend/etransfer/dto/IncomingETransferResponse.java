package com.bank.backend.etransfer.dto;

import com.bank.backend.etransfer.domain.ETransfer;
import com.bank.backend.etransfer.domain.ETransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What an incoming transfer looks like to the recipient.
 *
 * NEVER includes securityAnswerHash. Includes the security question
 * (recipient sees it on the claim form), the sender's account number
 * (so they know who it's from), and how many wrong attempts have been
 * made (so the UI can show "1 of 3 attempts used").
 */
public record IncomingETransferResponse(
    Long id,
    String recipientEmail,
    String recipientName,
    BigDecimal amount,
    String currency,
    String message,
    String securityQuestion,
    Boolean autoDeposited,
    ETransferStatus status,
    Instant expiresAt,
    Instant createdAt,
    int wrongAttempts,    // computed, not stored on ETransfer
    int attemptsRemaining
) {
    public static IncomingETransferResponse from(ETransfer e, long wrongAttempts) {
        int remaining = Math.max(0, 3 - (int) wrongAttempts);
        return new IncomingETransferResponse(
            e.getId(),
            e.getRecipientEmail(),
            e.getRecipientName(),
            e.getAmount(),
            e.getCurrency(),
            e.getMessage(),
            e.getSecurityQuestion(),
            e.getAutoDeposited(),
            e.getStatus(),
            e.getExpiresAt(),
            e.getCreatedAt(),
            (int) wrongAttempts,
            remaining
        );
    }
}