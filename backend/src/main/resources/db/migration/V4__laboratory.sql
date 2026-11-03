-- =====================================================================
-- V4: Laboratory — test catalog, orders, order items, uploaded reports
-- =====================================================================

CREATE TABLE lab_test_catalog (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    code              VARCHAR(20)   NOT NULL,
    name              VARCHAR(150)  NOT NULL,
    description       VARCHAR(500),
    sample_type       VARCHAR(50),
    price             DECIMAL(10, 2) NOT NULL,
    turnaround_hours  INT           NOT NULL DEFAULT 24,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_tests_code UNIQUE (code),
    CONSTRAINT uk_lab_tests_name UNIQUE (name),
    CONSTRAINT ck_lab_tests_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE lab_orders (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    order_no              VARCHAR(30)  NOT NULL,
    consultation_id       BIGINT       NOT NULL,
    patient_id            BIGINT       NOT NULL,
    doctor_id             BIGINT       NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ORDERED',
    priority              VARCHAR(10)  NOT NULL DEFAULT 'ROUTINE',
    clinical_notes        VARCHAR(1000),
    ordered_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sample_collected_at   DATETIME(6),
    completed_at          DATETIME(6),
    cancelled_at          DATETIME(6),
    cancellation_reason   VARCHAR(500),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version               BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_orders_no UNIQUE (order_no),
    CONSTRAINT fk_lab_orders_consultation FOREIGN KEY (consultation_id) REFERENCES consultations (id),
    CONSTRAINT fk_lab_orders_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_lab_orders_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_lab_orders_status CHECK (status IN ('ORDERED', 'SAMPLE_COLLECTED', 'PROCESSING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_lab_orders_priority CHECK (priority IN ('ROUTINE', 'URGENT')),
    INDEX idx_lab_orders_status (status, ordered_at),
    INDEX idx_lab_orders_patient (patient_id, ordered_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- price_at_order freezes the catalog price so later price edits never change history.
CREATE TABLE lab_order_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    lab_order_id    BIGINT        NOT NULL,
    test_id         BIGINT        NOT NULL,
    test_name       VARCHAR(150)  NOT NULL,
    price_at_order  DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_order_items UNIQUE (lab_order_id, test_id),
    CONSTRAINT fk_lab_order_items_order FOREIGN KEY (lab_order_id) REFERENCES lab_orders (id),
    CONSTRAINT fk_lab_order_items_test FOREIGN KEY (test_id) REFERENCES lab_test_catalog (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Files are stored on disk under a random server-generated name; only metadata is in MySQL.
CREATE TABLE lab_reports (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    lab_order_id         BIGINT        NOT NULL,
    uploaded_by_user_id  BIGINT        NOT NULL,
    original_filename    VARCHAR(255)  NOT NULL,
    stored_filename      VARCHAR(100)  NOT NULL,
    content_type         VARCHAR(100)  NOT NULL,
    file_size_bytes      BIGINT        NOT NULL,
    sha256               CHAR(64)      NOT NULL,
    result_summary       VARCHAR(1000),
    finalized            BOOLEAN       NOT NULL DEFAULT FALSE,
    uploaded_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finalized_at         DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_reports_stored UNIQUE (stored_filename),
    CONSTRAINT fk_lab_reports_order FOREIGN KEY (lab_order_id) REFERENCES lab_orders (id),
    CONSTRAINT fk_lab_reports_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_lab_reports_size CHECK (file_size_bytes > 0),
    INDEX idx_lab_reports_order (lab_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
