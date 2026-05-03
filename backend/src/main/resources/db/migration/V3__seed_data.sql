-- ============================================================================
-- V3: Seed data for development. NOT to be run in production.
--
-- Two customers, two users, three accounts demonstrating:
--   * a sole account (Alice's chequing)
--   * a joint account (Alice + Bob's savings)
--   * a TFSA (Bob's)
--
-- Password hashes are placeholders — Session 3 will overwrite when we wire
-- up real BCrypt-hashed registration. For now they're invalid hashes so
-- nobody can actually log in.
-- ============================================================================

-- Users
INSERT INTO users (id, email, password_hash) VALUES
    (1, 'alice@example.com', '$2a$12$placeholder.invalid.hash.do.not.use.session3.will.replace'),
    (2, 'bob@example.com',   '$2a$12$placeholder.invalid.hash.do.not.use.session3.will.replace');

-- Push the sequence past our hardcoded IDs so future inserts work
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- Customers
INSERT INTO customers (
    id, user_id,
    legal_first_name, legal_last_name, date_of_birth,
    email, phone,
    street_address, city, province, postal_code, country,
    occupation, id_type, id_number_last4, id_expiry_date,
    is_pep, kyc_status, risk_rating
) VALUES
    (1, 1,
     'Alice', 'Tremblay', '1990-04-15',
     'alice@example.com', '+14165550123',
     '123 Yonge Street', 'Toronto', 'ON', 'M5C1W4', 'CA',
     'Software Engineer', 'DRIVERS_LICENCE', '4321', '2030-04-15',
     false, 'APPROVED', 'LOW'),
    (2, 2,
     'Bob', 'Singh', '1985-11-02',
     'bob@example.com', '+16475550456',
     '456 Bay Street', 'Toronto', 'ON', 'M5H2Y2', 'CA',
     'Accountant', 'PASSPORT', '8765', '2029-11-02',
     false, 'APPROVED', 'LOW');

SELECT setval('customers_id_seq', (SELECT MAX(id) FROM customers));

-- Accounts
INSERT INTO accounts (id, account_number, account_type, currency, status, opened_at) VALUES
    (1, '100000000001', 'CHEQUING', 'CAD', 'ACTIVE', NOW()),
    (2, '100000000002', 'SAVINGS',  'CAD', 'ACTIVE', NOW()),  -- joint
    (3, '100000000003', 'TFSA',     'CAD', 'ACTIVE', NOW());

SELECT setval('accounts_id_seq', (SELECT MAX(id) FROM accounts));

-- Holder relationships
INSERT INTO account_holders (account_id, customer_id, role) VALUES
    (1, 1, 'PRIMARY_OWNER'),     -- Alice's sole chequing
    (2, 1, 'JOINT_OWNER'),       -- joint savings (Alice + Bob)
    (2, 2, 'JOINT_OWNER'),
    (3, 2, 'PRIMARY_OWNER');     -- Bob's TFSA