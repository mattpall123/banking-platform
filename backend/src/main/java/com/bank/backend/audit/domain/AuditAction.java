package com.bank.backend.audit.domain;

/**
 * Stable string identifiers for audit-able actions.
 *
 * Format: "<bounded-context>.<verb>"
 *
 * Adding entries here is fine; renaming or removing entries is a breaking
 * change to the audit log because historical rows reference these strings.
 */
public final class AuditAction {

    private AuditAction() {}

    // auth
    public static final String AUTH_REGISTER = "auth.register";
    public static final String AUTH_LOGIN    = "auth.login";
    public static final String AUTH_LOGOUT   = "auth.logout";
    public static final String AUTH_REFRESH  = "auth.refresh";

    // accounts
    public static final String ACCOUNT_DEPOSIT  = "account.deposit";
    public static final String ACCOUNT_WITHDRAW = "account.withdraw";

    // transfers
    public static final String TRANSFER_EXECUTE = "transfer.execute";
}