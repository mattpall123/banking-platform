-- ============================================================================
-- V2: Users, Customers, Accounts, and the join table for ownership.
--
-- Design notes:
--   * Three separate concepts: User (auth), Customer (KYC'd person),
--     Account (banking product). One Customer can hold multiple Accounts;
--     one Account can have multiple Customers (joint accounts).
--   * No money lives here — balances are derived from the ledger (V4+).
--   * Soft-close, never delete: regulatory retention is 5+ years.
--   * Audit columns on every table.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- USERS: authentication identity
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count  INTEGER      NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT users_email_lower_chk CHECK (email = LOWER(email))
);

CREATE INDEX idx_users_email ON users(email);

-- ---------------------------------------------------------------------------
-- CUSTOMERS: the bank's KYC'd customer record
--   Linked optionally to a User (a teller has User but no Customer; a
--   walk-in customer can have Customer but no online User yet).
-- ---------------------------------------------------------------------------
CREATE TABLE customers (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT UNIQUE REFERENCES users(id),

    -- Identity
    legal_first_name     VARCHAR(100) NOT NULL,
    legal_last_name      VARCHAR(100) NOT NULL,
    date_of_birth        DATE         NOT NULL,
    email                VARCHAR(255) NOT NULL,
    phone                VARCHAR(20)  NOT NULL,

    -- Address (FINTRAC: must be Canadian residential)
    street_address       VARCHAR(200) NOT NULL,
    city                 VARCHAR(100) NOT NULL,
    province             VARCHAR(2)   NOT NULL,
    postal_code          VARCHAR(10)  NOT NULL,
    country              VARCHAR(2)   NOT NULL DEFAULT 'CA',

    -- FINTRAC required fields
    occupation           VARCHAR(100) NOT NULL,
    id_type              VARCHAR(30)  NOT NULL,    -- DRIVERS_LICENCE, PASSPORT, PR_CARD
    id_number_last4      VARCHAR(4)   NOT NULL,    -- never store full ID number
    id_expiry_date       DATE         NOT NULL,
    is_pep               BOOLEAN      NOT NULL DEFAULT FALSE,

    -- KYC workflow state
    kyc_status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED, EXPIRED
    kyc_verified_at      TIMESTAMPTZ,
    kyc_verified_by      BIGINT REFERENCES users(id),

    -- Risk profile (drives transaction monitoring sensitivity)
    risk_rating          VARCHAR(10)  NOT NULL DEFAULT 'LOW',      -- LOW, MEDIUM, HIGH

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT customers_dob_past_chk      CHECK (date_of_birth < CURRENT_DATE),
    CONSTRAINT customers_id_expiry_chk     CHECK (id_expiry_date >= date_of_birth),
    CONSTRAINT customers_province_chk      CHECK (province IN
        ('AB','BC','MB','NB','NL','NS','NT','NU','ON','PE','QC','SK','YT')),
    CONSTRAINT customers_kyc_status_chk    CHECK (kyc_status IN
        ('PENDING','APPROVED','REJECTED','EXPIRED')),
    CONSTRAINT customers_risk_rating_chk   CHECK (risk_rating IN
        ('LOW','MEDIUM','HIGH')),
    CONSTRAINT customers_id_type_chk       CHECK (id_type IN
        ('DRIVERS_LICENCE','PASSPORT','PR_CARD'))
);

CREATE INDEX idx_customers_user_id     ON customers(user_id);
CREATE INDEX idx_customers_kyc_status  ON customers(kyc_status);
CREATE INDEX idx_customers_last_name   ON customers(legal_last_name);

-- ---------------------------------------------------------------------------
-- ACCOUNTS: banking product (the *container*, not the money)
--   account_number is the human-facing identifier; id is for joins.
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(20)  NOT NULL UNIQUE,
    account_type    VARCHAR(20)  NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'CAD',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    opened_at       TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT accounts_type_chk    CHECK (account_type IN ('CHEQUING','SAVINGS','TFSA')),
    CONSTRAINT accounts_status_chk  CHECK (status IN ('PENDING','ACTIVE','FROZEN','CLOSED')),
    CONSTRAINT accounts_currency_chk CHECK (currency IN ('CAD','USD'))
);

CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_type   ON accounts(account_type);

-- ---------------------------------------------------------------------------
-- ACCOUNT_HOLDERS: many-to-many between customers and accounts
--   The role tells us who is the primary owner vs. a joint owner vs. signer.
-- ---------------------------------------------------------------------------
CREATE TABLE account_holders (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT NOT NULL REFERENCES accounts(id),
    customer_id  BIGINT NOT NULL REFERENCES customers(id),
    role         VARCHAR(20) NOT NULL,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    removed_at   TIMESTAMPTZ,

    CONSTRAINT account_holders_role_chk CHECK (role IN
        ('PRIMARY_OWNER','JOINT_OWNER','SIGNER')),
    CONSTRAINT account_holders_unique UNIQUE (account_id, customer_id, role)
);

CREATE INDEX idx_account_holders_account  ON account_holders(account_id);
CREATE INDEX idx_account_holders_customer ON account_holders(customer_id);

-- ---------------------------------------------------------------------------
-- updated_at trigger function (we'll use this on every table going forward)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();