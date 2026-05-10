package com.bank.backend.etransfer.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bank.etransfer.* config binding.
 *
 * expiry-hours determines how long an unclaimed e-transfer stays PENDING
 * before the expiry runner refunds it. Real Interac is 30 days (720 hours);
 * we default to 1 hour for demo purposes — set to 720 in prod.
 */
@ConfigurationProperties(prefix = "bank.etransfer")
public record ETransferProperties(int expiryHours) {

    public ETransferProperties {
        if (expiryHours < 1) {
            throw new IllegalArgumentException("expiryHours must be >= 1");
        }
    }
}