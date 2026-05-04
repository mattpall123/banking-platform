-- ============================================================================
-- V5: Double-entry ledger
--
-- Three tables: ledger_accounts, journal_entries, postings.
--
-- The CORE INVARIANT is enforced at the DB level: every journal_entry must
-- have postings that sum to zero by currency. We use a deferred constraint
-- check so we can insert the entry and its postings within one transaction.
--
-- Banking accounts (from V2) are linked to ledger_accounts via a 1-to-1
-- relationship — each customer's chequing/savings is its own ledger
-- subaccount under the bank's "Customer Deposits" parent.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- LEDGER_ACCOUNTS: chart of accounts
--
-- account_type drives the sign convention. Customer deposits are LIABILITY
-- accounts (the bank owes the customer). Cash is an ASSET account.
-- ---------------------------------------------------------------------------
CREATE TABLE ledger_accounts (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL UNIQUE,   -- e.g. "CASH", "DEPOSIT:100000000001"
    name            VARCHAR(200) NOT NULL,
    account_type    VARCHAR(20)  NOT NULL,
    currency        VARCHAR(3)   NOT NULL,
    parent_id       BIGINT REFERENCES ledger_accounts(id),
    banking_account_id BIGINT REFERENCES accounts(id),  -- link to V2 accounts; null for GL accounts

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
        ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ledger_accounts_currency_chk CHECK (currency IN ('CAD','USD')),
    -- A banking account maps to exactly one ledger account
    CONSTRAINT ledger_accounts_banking_unique UNIQUE (banking_account_id)
);

CREATE INDEX idx_ledger_accounts_type      ON ledger_accounts(account_type);
CREATE INDEX idx_ledger_accounts_banking   ON ledger_accounts(banking_account_id);

CREATE TRIGGER trg_ledger_accounts_updated_at
    BEFORE UPDATE ON ledger_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- JOURNAL_ENTRIES: the immutable transaction record
--
-- Once written, never updated. Reversals are NEW journal entries with
-- opposite postings — that's the audit-friendly way.
-- ---------------------------------------------------------------------------
CREATE TABLE journal_entries (
    id                BIGSERIAL PRIMARY KEY,
    description       VARCHAR(500) NOT NULL,
    entry_type        VARCHAR(50)  NOT NULL,    -- DEPOSIT, WITHDRAWAL, TRANSFER, INTEREST, FEE, REVERSAL, ...
    idempotency_key   VARCHAR(100) UNIQUE,      -- caller-supplied dedup key; null for system-generated
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by_user_id BIGINT REFERENCES users(id),

    CONSTRAINT journal_entries_type_chk CHECK (entry_type IN
        ('DEPOSIT','WITHDRAWAL','TRANSFER','INTEREST','FEE','REVERSAL','ADJUSTMENT','OPENING'))
);

CREATE INDEX idx_journal_entries_occurred_at  ON journal_entries(occurred_at DESC);
CREATE INDEX idx_journal_entries_type         ON journal_entries(entry_type);
CREATE INDEX idx_journal_entries_created_by   ON journal_entries(created_by_user_id);

-- ---------------------------------------------------------------------------
-- POSTINGS: individual debit/credit lines under a journal entry
--
-- amount is SIGNED. Sum of all postings within a journal_entry must equal
-- zero per currency. The DB enforces this via a CONSTRAINT TRIGGER below.
--
-- amount stored at scale 4 (numeric(19,4)) — matches Money.STORAGE_SCALE.
-- ---------------------------------------------------------------------------
CREATE TABLE postings (
    id                  BIGSERIAL PRIMARY KEY,
    journal_entry_id    BIGINT       NOT NULL REFERENCES journal_entries(id),
    ledger_account_id   BIGINT       NOT NULL REFERENCES ledger_accounts(id),
    amount              NUMERIC(19,4) NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT postings_currency_chk CHECK (currency IN ('CAD','USD')),
    CONSTRAINT postings_amount_nonzero_chk CHECK (amount <> 0)
);

CREATE INDEX idx_postings_journal_entry  ON postings(journal_entry_id);
CREATE INDEX idx_postings_ledger_account ON postings(ledger_account_id);

-- ---------------------------------------------------------------------------
-- THE CRITICAL INVARIANT: postings under a journal_entry sum to zero
--   by currency.
--
-- Implemented as a constraint trigger that fires after each journal_entry's
-- postings are inserted. Marking it DEFERRABLE INITIALLY DEFERRED means the
-- check runs at COMMIT time, allowing us to insert the entry then its
-- postings in one transaction.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION verify_journal_entry_balanced()
RETURNS TRIGGER AS $$
DECLARE
    je_id BIGINT;
    bad_count INT;
BEGIN
    je_id := COALESCE(NEW.journal_entry_id, OLD.journal_entry_id);

    SELECT COUNT(*)
        INTO bad_count
        FROM (
            SELECT currency, SUM(amount) AS total
              FROM postings
             WHERE journal_entry_id = je_id
             GROUP BY currency
            HAVING SUM(amount) <> 0
        ) imbalanced;

    IF bad_count > 0 THEN
        RAISE EXCEPTION
            'Unbalanced journal entry %: postings do not sum to zero per currency',
            je_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_postings_balanced
    AFTER INSERT OR UPDATE OR DELETE ON postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION verify_journal_entry_balanced();