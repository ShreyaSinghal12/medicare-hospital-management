-- =====================================================================
-- V13: Refunds are credits, not reversals.
--   amount_paid     = everything received (sum of PAYMENT rows)
--   refunded_amount = everything given back (sum of REFUND rows)
--   balance due     = total_amount - amount_paid (a refund does not make
--                     the patient owe the money again)
-- Existing rows are recomputed from the payments table.
-- =====================================================================

ALTER TABLE invoices
    ADD COLUMN refunded_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00 AFTER amount_paid;

UPDATE invoices i
SET i.amount_paid     = COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id AND p.payment_type = 'PAYMENT'), 0),
    i.refunded_amount = COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id AND p.payment_type = 'REFUND'), 0);

UPDATE invoices
SET status = CASE
        WHEN status = 'CANCELLED' THEN 'CANCELLED'
        WHEN amount_paid > 0 AND refunded_amount = amount_paid THEN 'REFUNDED'
        WHEN amount_paid = 0 AND total_amount = 0 THEN 'PAID'
        WHEN amount_paid = 0 THEN 'UNPAID'
        WHEN amount_paid >= total_amount THEN 'PAID'
        ELSE 'PARTIALLY_PAID'
    END;

ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount_paid);
