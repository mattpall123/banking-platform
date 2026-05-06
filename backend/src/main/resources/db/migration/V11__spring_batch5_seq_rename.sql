-- ============================================================================
-- V11: Spring Batch 5 renamed its sequences to consolidate.
--
-- Spring Batch 4 used:  BATCH_JOB_INSTANCE_SEQ, BATCH_JOB_EXECUTION_SEQ
-- Spring Batch 5 uses:  BATCH_JOB_SEQ (single sequence for both)
--
-- Our V9 shipped the v4 names. We add the v5-named sequence here so the
-- modern JdbcJobInstanceDao can find it. We keep the v4 sequences in
-- place — harmless, and avoids any other code that might still hit them.
-- ============================================================================

CREATE SEQUENCE IF NOT EXISTS BATCH_JOB_SEQ START WITH 1 INCREMENT BY 1 NO CYCLE;