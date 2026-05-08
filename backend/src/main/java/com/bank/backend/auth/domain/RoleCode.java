package com.bank.backend.auth.domain;

/**
 * Stable string identifiers for roles.
 *
 * Format mirrors AuditAction: strings rather than an enum, because
 * historical DB rows reference these codes and renaming is a breaking
 * change. Constants here document the canonical set.
 */
public final class RoleCode {

    private RoleCode() {}

    public static final String CUSTOMER = "CUSTOMER";
    public static final String TELLER   = "TELLER";
    public static final String ADMIN    = "ADMIN";
}