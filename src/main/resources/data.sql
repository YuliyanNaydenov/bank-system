-- ═══════════════════════════════════════════════════════════════════════════
--  Примерни данни за Bank System
--  Изпълнява се при всяко стартиране, но добавя редове само ако липсват.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── Роли ────────────────────────────────────────────────────────────────────
INSERT IGNORE INTO roles (authority) VALUES ('admin');
INSERT IGNORE INTO roles (authority) VALUES ('employee');
INSERT IGNORE INTO roles (authority) VALUES ('client');

-- ── Потребители (BCrypt пароли) ──────────────────────────────────────────────
--  admin    / admin123
--  employee / employee123
--  client1  / client123
INSERT IGNORE INTO users (username, password, account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('admin',    '$2b$10$qT7Z2RzQ5Zu4IDxxAl8CFeVGdAoES/ZqcxDldKmusOEvLhRrsfrG2', 1, 1, 1, 1);

INSERT IGNORE INTO users (username, password, account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('employee', '$2b$10$rKtTvFdzcG1Rw/W9dIBjnOZMS7Sa/OjyDMo2gfhenLtKFLCL8ruai', 1, 1, 1, 1);

INSERT IGNORE INTO users (username, password, account_non_expired, account_non_locked, credentials_non_expired, enabled)
VALUES ('client1',  '$2b$10$L5IdigwuiYlKeqYc8SAPPuFOkI52Oafv.ZtQi3UwmWmSviA.FVbSm', 1, 1, 1, 1);

-- ── Свързване потребители ↔ роли ─────────────────────────────────────────────
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.authority = 'admin'    WHERE u.username = 'admin';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.authority = 'employee' WHERE u.username = 'employee';
INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u JOIN roles r ON r.authority = 'client'   WHERE u.username = 'client1';

-- ── Видове кредит (конфигурируеми от админ) ──────────────────────────────────
INSERT INTO credit_type (name, annual_interest_rate, max_amount, max_term_months)
SELECT * FROM (SELECT 'Потребителски', 10.50, 50000.00, 84) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM credit_type WHERE name = 'Потребителски');

INSERT INTO credit_type (name, annual_interest_rate, max_amount, max_term_months)
SELECT * FROM (SELECT 'Ипотечен', 3.20, 500000.00, 360) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM credit_type WHERE name = 'Ипотечен');

-- ── Клиенти ──────────────────────────────────────────────────────────────────
-- Физическо лице, свързано с потребител client1
INSERT INTO client (client_type, username, first_name, last_name, egn)
SELECT * FROM (SELECT 'INDIVIDUAL', 'client1', 'Иван', 'Петров', '8001011234') AS tmp
WHERE NOT EXISTS (SELECT 1 FROM client WHERE egn = '8001011234');

-- Физическо лице без вход в системата
INSERT INTO client (client_type, username, first_name, last_name, egn)
SELECT * FROM (SELECT 'INDIVIDUAL', NULL, 'Мария', 'Георгиева', '9203054321') AS tmp
WHERE NOT EXISTS (SELECT 1 FROM client WHERE egn = '9203054321');

-- Юридическо лице
INSERT INTO client (client_type, username, company_name, eik, representative_name)
SELECT * FROM (SELECT 'COMPANY', NULL, 'Софтуер ЕООД', '203456789', 'Георги Иванов') AS tmp
WHERE NOT EXISTS (SELECT 1 FROM client WHERE eik = '203456789');

-- ── Банкови сметки ───────────────────────────────────────────────────────────
INSERT INTO account (iban, balance, status, owner_id)
SELECT * FROM (SELECT 'BG18BANK00012345678901', 1250.00, 'ACTIVE',
    (SELECT id FROM client WHERE egn = '8001011234')) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM account WHERE iban = 'BG18BANK00012345678901');

INSERT INTO account (iban, balance, status, owner_id)
SELECT * FROM (SELECT 'BG18BANK00098765432109', 0.00, 'ACTIVE',
    (SELECT id FROM client WHERE eik = '203456789')) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM account WHERE iban = 'BG18BANK00098765432109');
