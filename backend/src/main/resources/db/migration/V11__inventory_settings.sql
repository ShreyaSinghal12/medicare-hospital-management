-- =====================================================================
-- V11: Medicine inventory (admin-managed)
--   * Supplier on each received batch.
--   * Configurable "expiring soon" window for inventory alerts.
-- =====================================================================

ALTER TABLE medicine_batches
    ADD COLUMN supplier VARCHAR(150) NULL AFTER purchase_price;

ALTER TABLE hospital_settings
    ADD COLUMN expiry_warning_days INT NOT NULL DEFAULT 60 AFTER reminder_hours_before,
    ADD CONSTRAINT ck_settings_expiry_warning CHECK (expiry_warning_days BETWEEN 1 AND 365);
