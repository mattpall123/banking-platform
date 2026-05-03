-- ============================================================================
-- V4: refresh_tokens
--
-- We store a SHA-256 hash of the token, not the token itself. Same principle
-- as passwords — if the DB leaks, attackers can't replay tokens.
--
-- Token rotation: every refresh issues a new token and revokes the old one.
-- This detects token theft: if an attacker uses a stolen token, the legit
-- user's next refresh fails (token already revoked), and we can panic-revoke
-- everything for that user.
-- ============================================================================

CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    token_hash   VARCHAR(64) NOT NULL UNIQUE,    -- SHA-256 hex = 64 chars
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,                    -- null = active
    replaced_by  BIGINT REFERENCES refresh_tokens(id),  -- audit trail of rotation
    created_from_ip VARCHAR(45),                 -- IPv6 max length

    CONSTRAINT refresh_tokens_expires_after_issued
        CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_active     ON refresh_tokens(user_id) WHERE revoked_at IS NULL;