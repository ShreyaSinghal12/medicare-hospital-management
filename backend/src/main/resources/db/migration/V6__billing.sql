-- =====================================================================
-- V6: Billing — invoices, invoice items, payments/refunds
-- No card data is ever stored. Payments are never deleted; refunds and
-- adjustments are separate, auditable rows.
-- =====================================================================

CREATE TABLE invoices (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_no          VARCHAR(30)    NOT NULL,
    patient_id          BIGINT         NOT NULL,
    appointment_id      BIGINT,
    subtotal            DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    discount_amount     DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    tax_rate_percent    DECIMAL(5, 2)  NOT NULL DEFAULT 0.00,
    tax_amount          DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    total_amount        DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    amount_paid         DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    status              VARCHAR(20)    NOT NULL DEFAULT 'UNPAID',
    notes               VARCHAR(500),
    issued_by_user_id   BIGINT         NOT NULL,
    issued_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cancelled_at        DATETIME(6),
    cancellation_reason VARCHAR(500),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_no UNIQUE (invoice_no),
    CONSTRAINT fk_invoices_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_invoices_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_invoices_issued_by FOREIGN KEY (issued_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_invoices_status CHECK (status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT ck_invoices_amounts CHECK (subtotal >= 0 AND discount_amount >= 0 AND tax_amount >= 0
                                          AND total_amount >= 0 AND amount_paid >= 0 AND amount_paid <= total_amount),
    INDEX idx_invoices_patient (patient_id, issued_at),
    INDEX idx_invoices_status (status, issued_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE invoice_items (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id    BIGINT         NOT NULL,
    item_type     VARCHAR(20)    NOT NULL,
    reference_id  BIGINT,
    description   VARCHAR(255)   NOT NULL,
    quantity      INT            NOT NULL DEFAULT 1,
    unit_price    DECIMAL(12, 2) NOT NULL,
    line_total    DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT ck_invoice_items_type CHECK (item_type IN ('CONSULTATION', 'LAB_TEST', 'PHARMACY', 'PROCEDURE', 'OTHER')),
    CONSTRAINT ck_invoice_items_values CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0),
    INDEX idx_invoice_items_reference (item_type, reference_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- method values ending in _SIMULATED are clearly labelled demo payment modes.
CREATE TABLE payments (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    payment_no             VARCHAR(30)    NOT NULL,
    invoice_id             BIGINT         NOT NULL,
    payment_type           VARCHAR(20)    NOT NULL DEFAULT 'PAYMENT',
    method                 VARCHAR(30)    NOT NULL,
    amount                 DECIMAL(12, 2) NOT NULL,
    transaction_reference  VARCHAR(100),
    notes                  VARCHAR(500),
    original_payment_id    BIGINT,
    recorded_by_user_id    BIGINT         NOT NULL,
    paid_at                DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_no UNIQUE (payment_no),
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_payments_original FOREIGN KEY (original_payment_id) REFERENCES payments (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (recorded_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_payments_type CHECK (payment_type IN ('PAYMENT', 'REFUND')),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH', 'CARD_SIMULATED', 'UPI_SIMULATED', 'BANK_TRANSFER_SIMULATED')),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    INDEX idx_payments_invoice (invoice_id, paid_at),
    INDEX idx_payments_paid_at (paid_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
