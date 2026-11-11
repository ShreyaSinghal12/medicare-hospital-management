-- =====================================================================
-- V12: Billing
--   * Prices recorded when a service is booked or dispensed, so later
--     catalog/fee changes never change what a patient is charged.
--   * Each billable charge can be on at most one active invoice:
--     billed_reference (e.g. 'CONSULTATION:42') is unique and cleared
--     when an unpaid invoice is cancelled, releasing the charge.
-- =====================================================================

ALTER TABLE appointments
    ADD COLUMN consultation_fee DECIMAL(10, 2) NULL;

ALTER TABLE dispensing_items
    ADD COLUMN unit_price DECIMAL(12, 2) NULL;

ALTER TABLE invoice_items
    ADD COLUMN billed_reference VARCHAR(40) NULL,
    ADD CONSTRAINT uk_invoice_items_billed UNIQUE (billed_reference);
