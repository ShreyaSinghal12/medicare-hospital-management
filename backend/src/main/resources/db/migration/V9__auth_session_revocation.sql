-- =====================================================================
-- V9 (Phase 2): session revocation support.
-- password_changed_at lets the server reject sessions that were created
-- before the last password change/reset, so a reset logs out every other
-- device. Deactivating a user is enforced the same way (users.active).
-- =====================================================================

ALTER TABLE users
    ADD COLUMN password_changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER password_hash;
