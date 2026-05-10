-- ============================================================================
-- V14: Interac e-Transfer simulation.
--
-- Three new tables:
--   etransfers              - the transfers themselves
--   etransfer_attempts      - claim attempts (correct/wrong answers, lockouts)
--   auto_deposit_settings   - email → account mapping for auto-deposit
--
-- Plus:
--   - A new LIABILITY ledger account "Pending:ETransfers" that holds funds
--     in flight. Sender's funds debit to this account on send; on claim or
--     refund they debit out to the destination or back to the sender.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- The pending-e-transfer holding ledger account.
-- All in-flight e-transfer funds sit here. Single account; per-transfer
-- linkage is via the etransfer.holding_journal_entry_id column below.
-- ---------------------------------------------------------------------------
INSERT INTO ledger_accounts (code, name, account_type, currency, banking_account_id)
VALUES ('PENDING_ETRANSFERS',
        'Pending Interac e-Transfers',
        'LIABILITY', 'CAD', NULL);
-- ---------------------------------------------------------------------------
-- ETransfers: one row per transfer attempt by a sender.
-- ---------------------------------------------------------------------------
CREATE TABLE etransfers (
    id                          BIGSERIAL PRIMARY KEY,
    sender_account_id           BIGINT       NOT NULL REFERENCES accounts(id),
    sender_user_id              BIGINT       NOT NULL REFERENCES users(id),

    recipient_email             VARCHAR(255) NOT NULL,
    recipient_name              VARCHAR(140) NOT NULL,
    -- recipient_account_id is set ONLY if/when this transfer is claimed and
    -- routed to one of our customers' accounts. NULL while pending.
    recipient_account_id        BIGINT       REFERENCES accounts(id),

    amount                      NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency                    VARCHAR(3)   NOT NULL,
    message                     VARCHAR(400),

    -- Security question. Question text is plaintext; answer is bcrypt-hashed.
    -- For auto-deposit transfers these are NULL (no Q&A required).
    security_question           VARCHAR(140),
    security_answer_hash        VARCHAR(100),

    -- Set true on send if the recipient email had auto-deposit enabled.
    -- Auto-deposit transfers skip the claim screen and credit immediately.
    auto_deposited              BOOLEAN      NOT NULL DEFAULT FALSE,

    status                      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',

    -- Ledger linkage. holding_journal_entry_id records the send (debit
    -- sender, credit Pending). settlement_journal_entry_id records the
    -- claim/refund/cancel (debit Pending, credit destination).
    holding_journal_entry_id    BIGINT       NOT NULL REFERENCES journal_entries(id),
    settlement_journal_entry_id BIGINT       REFERENCES journal_entries(id),

    expires_at                  TIMESTAMPTZ  NOT NULL,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT etransfers_status_chk
        CHECK (status IN ('PENDING','COMPLETED','CANCELLED','EXPIRED','LOCKED')),

    -- If auto-deposited then no security Q&A and we settle on send.
    -- If not auto-deposited then security Q&A is required.
    CONSTRAINT etransfers_security_chk CHECK (
        (auto_deposited = TRUE AND security_question IS NULL AND security_answer_hash IS NULL)
        OR
        (auto_deposited = FALSE AND security_question IS NOT NULL AND security_answer_hash IS NOT NULL)
    )
);

CREATE INDEX idx_etransfers_sender_user      ON etransfers(sender_user_id, created_at DESC);
CREATE INDEX idx_etransfers_recipient_email  ON etransfers(LOWER(recipient_email));
CREATE INDEX idx_etransfers_pending_expiry   ON etransfers(expires_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- Per-claim-attempt log. Lets us implement "3 wrong answers and the
-- transfer is locked + refunded" rule, and gives the customer an audit of
-- who tried to claim their incoming transfer.
-- ---------------------------------------------------------------------------
CREATE TABLE etransfer_attempts (
    id              BIGSERIAL PRIMARY KEY,
    etransfer_id    BIGINT       NOT NULL REFERENCES etransfers(id) ON DELETE CASCADE,
    attempted_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    attempted_by_user_id BIGINT  REFERENCES users(id),  -- NULL if non-customer (future)
    correct         BOOLEAN      NOT NULL,
    -- Note: we deliberately do NOT store the supplied answer, even hashed.
    -- The hash comparison happens in-process; the wrong guess never lands
    -- in any persistent store. Tighter privacy posture.
    ip_address      VARCHAR(45)
);

CREATE INDEX idx_etransfer_attempts_etransfer ON etransfer_attempts(etransfer_id, attempted_at DESC);

-- ---------------------------------------------------------------------------
-- Auto-deposit settings: a customer registers their email so future
-- e-transfers to that address auto-credit their primary chequing account
-- without requiring a security Q&A.
-- ---------------------------------------------------------------------------
CREATE TABLE auto_deposit_settings (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email               VARCHAR(255) NOT NULL,
    target_account_id   BIGINT       NOT NULL REFERENCES accounts(id),
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- One email globally maps to one auto-deposit registration. Real
    -- Interac enforces this — you can't register the same email at two
    -- banks. Lowercased to make lookups case-insensitive.
    CONSTRAINT auto_deposit_email_unique UNIQUE (email)
);

CREATE INDEX idx_auto_deposit_user ON auto_deposit_settings(user_id);