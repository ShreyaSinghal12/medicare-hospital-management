-- =====================================================================
-- V5: Pharmacy — medicine catalog, batches, stock ledger, dispensing
-- =====================================================================

CREATE TABLE medicines (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150)  NOT NULL,
    generic_name    VARCHAR(150),
    manufacturer    VARCHAR(150)  NOT NULL,
    category        VARCHAR(50)   NOT NULL,
    strength        VARCHAR(50)   NOT NULL,
    unit            VARCHAR(20)   NOT NULL,
    unit_price      DECIMAL(10, 2) NOT NULL,
    reorder_level   INT           NOT NULL DEFAULT 20,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_medicines_identity UNIQUE (name, strength, manufacturer),
    CONSTRAINT ck_medicines_price CHECK (unit_price >= 0),
    CONSTRAINT ck_medicines_reorder CHECK (reorder_level >= 0),
    INDEX idx_medicines_category (category),
    INDEX idx_medicines_generic (generic_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE prescription_items
    ADD CONSTRAINT fk_prescription_items_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id);

-- quantity_available >= 0 is enforced by the database, not just the service layer.
CREATE TABLE medicine_batches (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    medicine_id         BIGINT        NOT NULL,
    batch_number        VARCHAR(50)   NOT NULL,
    expiry_date         DATE          NOT NULL,
    quantity_received   INT           NOT NULL,
    quantity_available  INT           NOT NULL,
    purchase_price      DECIMAL(10, 2),
    received_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_batches_medicine_batch UNIQUE (medicine_id, batch_number),
    CONSTRAINT fk_batches_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id),
    CONSTRAINT ck_batches_received CHECK (quantity_received > 0),
    CONSTRAINT ck_batches_available CHECK (quantity_available >= 0),
    INDEX idx_batches_expiry (expiry_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE dispensing_records (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    dispensing_no          VARCHAR(30)  NOT NULL,
    prescription_id        BIGINT       NOT NULL,
    patient_id             BIGINT       NOT NULL,
    dispensed_by_user_id   BIGINT       NOT NULL,
    notes                  VARCHAR(500),
    dispensed_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_dispensing_no UNIQUE (dispensing_no),
    CONSTRAINT fk_dispensing_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id),
    CONSTRAINT fk_dispensing_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_dispensing_user FOREIGN KEY (dispensed_by_user_id) REFERENCES users (id),
    INDEX idx_dispensing_prescription (prescription_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per batch drawn from when dispensing a prescription line.
CREATE TABLE dispensing_items (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    dispensing_id         BIGINT NOT NULL,
    prescription_item_id  BIGINT NOT NULL,
    medicine_id           BIGINT NOT NULL,
    batch_id              BIGINT NOT NULL,
    quantity              INT    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dispensing_items_record FOREIGN KEY (dispensing_id) REFERENCES dispensing_records (id),
    CONSTRAINT fk_dispensing_items_rx_item FOREIGN KEY (prescription_item_id) REFERENCES prescription_items (id),
    CONSTRAINT fk_dispensing_items_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id),
    CONSTRAINT fk_dispensing_items_batch FOREIGN KEY (batch_id) REFERENCES medicine_batches (id),
    CONSTRAINT ck_dispensing_items_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Append-only stock ledger. quantity is always positive; direction comes from txn_type.
CREATE TABLE inventory_transactions (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    medicine_id           BIGINT       NOT NULL,
    batch_id              BIGINT       NOT NULL,
    txn_type              VARCHAR(20)  NOT NULL,
    quantity              INT          NOT NULL,
    balance_after         INT          NOT NULL,
    dispensing_id         BIGINT,
    reference             VARCHAR(100),
    notes                 VARCHAR(500),
    performed_by_user_id  BIGINT       NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_inv_txn_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id),
    CONSTRAINT fk_inv_txn_batch FOREIGN KEY (batch_id) REFERENCES medicine_batches (id),
    CONSTRAINT fk_inv_txn_dispensing FOREIGN KEY (dispensing_id) REFERENCES dispensing_records (id),
    CONSTRAINT fk_inv_txn_user FOREIGN KEY (performed_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_inv_txn_type CHECK (txn_type IN ('STOCK_IN', 'STOCK_OUT', 'DISPENSE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'EXPIRED_WRITE_OFF')),
    CONSTRAINT ck_inv_txn_qty CHECK (quantity > 0),
    CONSTRAINT ck_inv_txn_balance CHECK (balance_after >= 0),
    INDEX idx_inv_txn_medicine (medicine_id, created_at),
    INDEX idx_inv_txn_type (txn_type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
