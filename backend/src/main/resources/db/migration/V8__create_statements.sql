-- ============================================================================
-- V8: Monthly statements + Spring Batch metadata.
--
-- TABLES:
--   statements          - one row per generated PDF (account × period)
--   statement_runs      - one row per batch job execution (success/failure)
--   plus Spring Batch's built-in tables (BATCH_JOB_INSTANCE etc.)
--     created by Spring Batch's auto-config from its bundled DDL
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Per-statement record. The actual PDF lives on disk at file_path.
--
-- Why store path-not-bytes:
--   1. Statements are immutable once issued; filesystem (or object storage)
--      is the right primary store
--   2. Postgres BLOB storage isn't free; pulling 50MB of PDF into a query
--      result is bad UX
--   3. Easy migration to S3/GCS later — only the path scheme changes
-- ---------------------------------------------------------------------------
CREATE TABLE statements (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT       NOT NULL REFERENCES accounts(id),
    period_year     INT          NOT NULL,
    period_month    INT          NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    file_path       VARCHAR(500) NOT NULL,
    file_size_bytes BIGINT       NOT NULL,
    transaction_count INT        NOT NULL,
    opening_balance NUMERIC(19,4) NOT NULL,
    closing_balance NUMERIC(19,4) NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- A given account gets at most one statement per period.
    -- Re-running the job for the same period replaces, not duplicates.
    CONSTRAINT statements_account_period_unique UNIQUE (account_id, period_year, period_month)
);

CREATE INDEX idx_statements_account_id   ON statements(account_id);
CREATE INDEX idx_statements_period       ON statements(period_year, period_month);
CREATE INDEX idx_statements_generated_at ON statements(generated_at DESC);

-- ---------------------------------------------------------------------------
-- Per-batch-execution record. One row per "run statement generation for
-- April 2026" attempt, regardless of success.
-- ---------------------------------------------------------------------------
CREATE TABLE statement_runs (
    id                 BIGSERIAL PRIMARY KEY,
    period_year        INT         NOT NULL,
    period_month       INT         NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    started_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at        TIMESTAMPTZ,
    status             VARCHAR(20) NOT NULL,                    -- RUNNING | COMPLETED | FAILED
    statements_created INT         NOT NULL DEFAULT 0,
    error_count        INT         NOT NULL DEFAULT 0,
    error_message      VARCHAR(2000),
    triggered_by_user_id BIGINT REFERENCES users(id),           -- null for cron, set for manual

    CONSTRAINT statement_runs_status_chk CHECK (status IN ('RUNNING','COMPLETED','FAILED'))
);

CREATE INDEX idx_statement_runs_period     ON statement_runs(period_year, period_month);
CREATE INDEX idx_statement_runs_started_at ON statement_runs(started_at DESC);