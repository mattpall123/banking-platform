-- ============================================================================
-- V15: Add ETRANSFER to the journal_entries.entry_type whitelist.
--
-- The CHECK constraint enumerates allowed values; we need to drop and
-- recreate it with ETRANSFER added. Postgres doesn't support modifying
-- CHECK constraints in place.
-- ============================================================================

ALTER TABLE journal_entries DROP CONSTRAINT journal_entries_type_chk;

ALTER TABLE journal_entries ADD CONSTRAINT journal_entries_type_chk
    CHECK (entry_type IN (
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER',
        'ETRANSFER',
        'INTEREST',
        'FEE',
        'REVERSAL',
        'ADJUSTMENT',
        'OPENING'
    ));