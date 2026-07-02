-- Role-based authorization: every user gets USER by default.
-- Admin promotion is an operational step for now, e.g.:
--   UPDATE users SET role = 'ADMIN' WHERE service_number = '...';
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';