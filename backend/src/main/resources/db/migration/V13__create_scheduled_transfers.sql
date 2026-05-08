-- ============================================================================
-- V13: Scheduled (recurring) transfers.
--
-- A scheduled_transfer is an INTENT — "transfer $200 from account 1 to
-- account 2 every Friday." The runner in Part 4 polls for due intents and
-- executes them via TransferService.
--
-- Each execution attempt produces a row in scheduled_transfer_executions,
-- so customers can audit what was tried, when, and what happened.
-- ============================================================================

CREATE TABLE scheduled_transfers (
    id                          BIGSERIAL PRIMARY KEY,
    source_account_id           BIGINT       NOT NULL REFERENCES accounts(id),
    destination_account_number  VARCHAR(20)  NOT NULL,
    amount                      NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency                    VARCHAR(3)   NOT NULL,
    description                 VARCHAR(140) NOT NULL,

    frequency                   VARCHAR(20)  NOT NULL,    -- ONCE | DAILY | WEEKLY | MONTHLY
    day_of_week                 INT          CHECK (day_of_week BETWEEN 1 AND 7),     -- WEEKLY only
    day_of_month                INT          CHECK (day_of_month BETWEEN 1 AND 28),   -- MONTHLY only
    -- We cap day_of_month at 28 to dodge "what if it's Feb 30?" — simplest correct rule.

    start_date                  DATE         NOT NULL,
    end_date                    DATE,                     -- nullable = no end
    next_run_at                 TIMESTAMPTZ  NOT NULL,    -- when the runner should next consider this
    status                      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_by_user_id          BIGINT       NOT NULL REFERENCES users(id),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT st_frequency_chk CHECK (frequency IN ('ONCE','DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT st_status_chk    CHECK (status IN ('ACTIVE','PAUSED','EXPIRED','CANCELLED'))
);

-- The runner's hottest query: "give me all ACTIVE schedules due now." Index for it.
CREATE INDEX idx_scheduled_transfers_due
    ON scheduled_transfers (next_run_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_scheduled_transfers_source ON scheduled_transfers(source_account_id);
CREATE INDEX idx_scheduled_transfers_creator ON scheduled_transfers(created_by_user_id);

-- ---------------------------------------------------------------------------
-- Per-attempt execution log. The runner writes one row per scheduled run,
-- whether it succeeds or fails. Becomes the customer's "did it work?" view.
-- ---------------------------------------------------------------------------
CREATE TABLE scheduled_transfer_executions (
    id                       BIGSERIAL PRIMARY KEY,
    scheduled_transfer_id    BIGINT       NOT NULL REFERENCES scheduled_transfers(id) ON DELETE CASCADE,
    planned_at               TIMESTAMPTZ  NOT NULL,        -- when this run was supposed to fire
    executed_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status                   VARCHAR(30)  NOT NULL,
    journal_entry_id         BIGINT       REFERENCES journal_entries(id),
    error_message            VARCHAR(500),

    -- Idempotency key for the underlying transfer: the runner uses
    -- (scheduled_transfer_id || ':' || planned_at_epoch) so re-runs
    -- of the same scheduled point don't double-fire.
    idempotency_key          VARCHAR(100) NOT NULL,

    CONSTRAINT ste_status_chk CHECK (status IN ('SUCCESS','INSUFFICIENT_FUNDS','FAILED')),
    CONSTRAINT ste_idem_unique UNIQUE (idempotency_key)
);

CREATE INDEX idx_ste_scheduled ON scheduled_transfer_executions(scheduled_transfer_id, planned_at DESC);