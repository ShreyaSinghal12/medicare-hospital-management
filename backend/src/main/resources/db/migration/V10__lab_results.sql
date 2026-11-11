-- =====================================================================
-- V10: Laboratory results for the four-role model
--   * Catalog: unit and reference range typed by the administrator (display only).
--   * Order items: result value and comment typed by the ordering doctor, plus a
--     snapshot of unit and reference range at order time (catalog edits never change history).
--   * One report file per order; results live on the items, so the unused summary column goes.
-- =====================================================================

ALTER TABLE lab_test_catalog
    ADD COLUMN unit            VARCHAR(30)  NULL AFTER sample_type,
    ADD COLUMN reference_range VARCHAR(100) NULL AFTER unit;

ALTER TABLE lab_order_items
    ADD COLUMN unit             VARCHAR(30)  NULL,
    ADD COLUMN reference_range  VARCHAR(100) NULL,
    ADD COLUMN result_value     VARCHAR(100) NULL,
    ADD COLUMN result_comment   VARCHAR(500) NULL;

ALTER TABLE lab_reports
    DROP COLUMN result_summary,
    ADD CONSTRAINT uk_lab_reports_order UNIQUE (lab_order_id);

-- Fictional display values for the seeded catalog (administrators can change them).
UPDATE lab_test_catalog SET unit = 'mg/dL', reference_range = '70–100' WHERE code = 'FBS';
UPDATE lab_test_catalog SET unit = '%',     reference_range = '4.0–5.6' WHERE code = 'HBA1C';
UPDATE lab_test_catalog SET unit = 'mIU/L', reference_range = '0.4–4.0' WHERE code = 'TSH';
