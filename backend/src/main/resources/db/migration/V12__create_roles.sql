-- ============================================================================
-- V12: Role-based access control (RBAC).
--
-- Two new tables:
--   roles      - the lookup table of valid role codes (CUSTOMER, TELLER, ADMIN)
--   user_roles - many-to-many between users and roles
--
-- We seed CUSTOMER for every existing user so nobody loses access on deploy.
-- Tellers and admins are created explicitly via the admin endpoint or the
-- boot-time promotion (dev only).
-- ============================================================================

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE,       -- CUSTOMER | TELLER | ADMIN
    description VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO roles (code, description) VALUES
    ('CUSTOMER', 'Standard banking customer — sees own accounts, makes own transfers'),
    ('TELLER',   'Branch staff — can perform transactions on a customer''s behalf, freeze accounts'),
    ('ADMIN',    'Platform administrator — full access including role management and batch jobs');

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id),
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by_user_id BIGINT REFERENCES users(id),

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- Backfill: every existing user gets CUSTOMER (no audit trail for this since
-- it's a migration, not a runtime grant).
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE r.code = 'CUSTOMER'
ON CONFLICT DO NOTHING;