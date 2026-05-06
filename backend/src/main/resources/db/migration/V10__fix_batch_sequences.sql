-- ============================================================================
-- V10: Fix Spring Batch sequences.
--
-- The DDL we shipped in V9 was the canonical Postgres script from older
-- Spring Batch versions. Spring Batch 5 expects the sequences to behave
-- like normal Postgres BIGSERIAL — start at 1, increment by 1.
-- ============================================================================

DROP SEQUENCE IF EXISTS BATCH_STEP_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_EXECUTION_SEQ;
DROP SEQUENCE IF EXISTS BATCH_JOB_INSTANCE_SEQ;

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ  START WITH 1 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_INSTANCE_SEQ   START WITH 1 INCREMENT BY 1 NO CYCLE;