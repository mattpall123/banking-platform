-- ============================================================================
-- V7: Append-only audit log with hash chaining for tamper evidence.
--
-- Every state-changing API call writes one row here, in the SAME transaction
-- as the business operation. If the audit insert fails, the operation rolls
-- back. Reads are NOT audited (high volume, low forensic value).
--
-- HASH CHAIN: every row stores SHA-256 of (its own canonical fields +
-- previous row's hash). This makes the log tamper-evident — you cannot
-- silently modify a past row without breaking the chain.
--
-- We DO NOT enforce the hash chain via DB constraint; it's computed in the
-- application's AuditService where we control serialisation. A separate
-- verifier endpoint can re-walk the chain to detect tampering.
-- ============================================================================

CREATE TABLE audit_events (
    id              BIGSERIAL PRIMARY KEY,

    -- WHO
    user_id         BIGINT REFERENCES users(id),     -- nullable for failed-login attempts
    actor_email     VARCHAR(255),                    -- captured even when user_id is null

    -- WHAT
    action          VARCHAR(100) NOT NULL,           -- e.g. "auth.login", "transfer.execute"
    resource_type   VARCHAR(100),                    -- e.g. "Account", "JournalEntry"
    resource_id     VARCHAR(100),                    -- the affected entity's id (string for flexibility)
    outcome         VARCHAR(20) NOT NULL,            -- SUCCESS | FAILURE
    failure_reason  VARCHAR(500),                    -- nullable; populated on FAILURE

    -- WHERE
    ip_address      VARCHAR(64),                     -- IPv4 or IPv6
    user_agent      VARCHAR(500),

    -- CONTEXT
    metadata_json   TEXT,                            -- arbitrary key-value JSON (e.g. amount, currency)

    -- WHEN (server-side; never trust client)
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- TAMPER EVIDENCE
    prev_hash       VARCHAR(64),                     -- hex SHA-256 of previous row, NULL for first row
    row_hash        VARCHAR(64) NOT NULL,            -- hex SHA-256 of (canonical row data + prev_hash)

    CONSTRAINT audit_events_outcome_chk CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_events_user_id     ON audit_events(user_id);
CREATE INDEX idx_audit_events_action      ON audit_events(action);
CREATE INDEX idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_events_outcome     ON audit_events(outcome);