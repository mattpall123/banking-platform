package com.bank.backend.auth.service;

/**
 * Signals that a *revoked* refresh token was presented for refresh.
 * This is a strong indicator of token theft — the caller should revoke
 * every active token for this user, forcing a full re-login.
 */
public class RefreshTokenReusedException extends RuntimeException {
    private final Long userId;

    public RefreshTokenReusedException(Long userId) {
        super("Refresh token reuse detected for user " + userId);
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
}