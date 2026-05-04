-- ============================================================================
-- V6: Seed the chart of accounts and bootstrap the bank with opening capital.
--
-- After this migration:
--   - The bank has a Cash account (ASSET) with $1,000,000 CAD.
--   - That money came from Bank Equity (Opening) (EQUITY).
--   - Each banking account from V3 has its own LIABILITY ledger account
--     under "Customer Deposits", with zero balance.
--
-- This is what real banks do conceptually — they're capitalised on day one
-- and customer deposits become liabilities that ride on top of that base.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- General-ledger accounts (no banking_account_id link)
-- ---------------------------------------------------------------------------
INSERT INTO ledger_accounts (id, code, name, account_type, currency) VALUES
    (1, 'CASH',            'Cash',                  'ASSET',     'CAD'),
    (2, 'EQUITY:OPENING',  'Bank Equity (Opening)', 'EQUITY',    'CAD'),
    (3, 'DEPOSITS:PARENT', 'Customer Deposits',     'LIABILITY', 'CAD');

-- Advance the sequence past the hardcoded IDs BEFORE the next insert
SELECT setval('ledger_accounts_id_seq', (SELECT MAX(id) FROM ledger_accounts));

-- ---------------------------------------------------------------------------
-- Per-account liability subaccounts for V3's seeded banking accounts
--   (Alice's chequing, joint savings, Bob's TFSA)
-- ---------------------------------------------------------------------------
INSERT INTO ledger_accounts (code, name, account_type, currency, parent_id, banking_account_id)
SELECT
    'DEPOSIT:' || a.account_number,
    'Customer Deposit — ' || a.account_number,
    'LIABILITY',
    a.currency,
    3,
    a.id
FROM accounts a;

-- ---------------------------------------------------------------------------
-- Opening journal entry: capitalise the bank with $1,000,000 CAD
--
-- Sign convention recap: amount is SIGNED, debits and credits sum to zero.
--   Cash (ASSET):    +amount = balance up
--   Equity:          stored as -amount here so the entry sums to zero;
--                    the equity "balance" is read as -SUM(amount) elsewhere.
-- ---------------------------------------------------------------------------
INSERT INTO journal_entries (id, description, entry_type, idempotency_key, occurred_at)
VALUES (1, 'Opening capital', 'OPENING', 'OPENING-2026', NOW());

INSERT INTO postings (journal_entry_id, ledger_account_id, amount, currency) VALUES
    (1, 1, 1000000.0000, 'CAD'),    -- +1,000,000 to Cash
    (1, 2, -1000000.0000, 'CAD');   -- -1,000,000 to Equity

SELECT setval('journal_entries_id_seq', (SELECT MAX(id) FROM journal_entries));