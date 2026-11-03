-- =====================================================================
-- V3: Clinical records — consultations, amendments, allergies,
-- conditions, prescriptions. All clinical content is entered manually
-- by an authorised doctor; the system never suggests diagnoses/doses.
-- =====================================================================

CREATE TABLE consultations (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_id   BIGINT      NOT NULL,
    patient_id       BIGINT      NOT NULL,
    doctor_id        BIGINT      NOT NULL,
    symptoms         TEXT,
    observations     TEXT,
    diagnosis        TEXT,
    treatment_plan   TEXT,
    notes            TEXT,
    follow_up_date   DATE,
    follow_up_notes  VARCHAR(500),
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    finalized_at     DATETIME(6),
    amendment_count  INT         NOT NULL DEFAULT 0,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version          BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_consultations_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_consultations_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_consultations_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_consultations_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_consultations_status CHECK (status IN ('DRAFT', 'FINALIZED')),
    INDEX idx_consultations_patient (patient_id, created_at),
    INDEX idx_consultations_doctor (doctor_id, created_at),
    INDEX idx_consultations_follow_up (follow_up_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Finalised consultations are never silently overwritten: each amendment stores
-- the previous values (JSON snapshot) plus who amended and why.
CREATE TABLE consultation_amendments (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    consultation_id       BIGINT       NOT NULL,
    amended_by_doctor_id  BIGINT       NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    previous_snapshot     JSON         NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_amendments_consultation FOREIGN KEY (consultation_id) REFERENCES consultations (id),
    CONSTRAINT fk_amendments_doctor FOREIGN KEY (amended_by_doctor_id) REFERENCES doctors (id),
    INDEX idx_amendments_consultation (consultation_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE patient_allergies (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id             BIGINT       NOT NULL,
    allergen               VARCHAR(150) NOT NULL,
    reaction               VARCHAR(255),
    severity               VARCHAR(20),
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    recorded_by_doctor_id  BIGINT,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_allergies_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_allergies_doctor FOREIGN KEY (recorded_by_doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_allergies_severity CHECK (severity IS NULL OR severity IN ('MILD', 'MODERATE', 'SEVERE')),
    INDEX idx_allergies_patient (patient_id, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE patient_conditions (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    patient_id             BIGINT       NOT NULL,
    condition_name         VARCHAR(150) NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    diagnosed_on           DATE,
    notes                  VARCHAR(500),
    recorded_by_doctor_id  BIGINT,
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_conditions_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_conditions_doctor FOREIGN KEY (recorded_by_doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_conditions_status CHECK (status IN ('ACTIVE', 'RESOLVED', 'CHRONIC')),
    INDEX idx_conditions_patient (patient_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Versioned prescriptions: a FINALIZED prescription is immutable. An amendment
-- creates a new row (version_number + 1, previous_version_id set) and marks the
-- old one SUPERSEDED.
CREATE TABLE prescriptions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    prescription_no      VARCHAR(30)  NOT NULL,
    consultation_id      BIGINT       NOT NULL,
    patient_id           BIGINT       NOT NULL,
    doctor_id            BIGINT       NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    version_number       INT          NOT NULL DEFAULT 1,
    previous_version_id  BIGINT,
    amendment_reason     VARCHAR(500),
    general_instructions VARCHAR(1000),
    finalized_at         DATETIME(6),
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_prescriptions_no_version UNIQUE (prescription_no, version_number),
    CONSTRAINT fk_prescriptions_consultation FOREIGN KEY (consultation_id) REFERENCES consultations (id),
    CONSTRAINT fk_prescriptions_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_prescriptions_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT fk_prescriptions_previous FOREIGN KEY (previous_version_id) REFERENCES prescriptions (id),
    CONSTRAINT ck_prescriptions_status CHECK (status IN ('DRAFT', 'FINALIZED', 'SUPERSEDED')),
    INDEX idx_prescriptions_patient (patient_id, created_at),
    INDEX idx_prescriptions_doctor (doctor_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- medicine_id is an optional link to the pharmacy catalog (added in V5);
-- medicine_name is always stored exactly as the doctor wrote it.
CREATE TABLE prescription_items (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    prescription_id  BIGINT       NOT NULL,
    medicine_id      BIGINT,
    medicine_name    VARCHAR(150) NOT NULL,
    strength         VARCHAR(50),
    dosage           VARCHAR(100) NOT NULL,
    frequency        VARCHAR(100) NOT NULL,
    duration         VARCHAR(100) NOT NULL,
    instructions     VARCHAR(500),
    quantity         INT,
    sort_order       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_prescription_items_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE CASCADE,
    CONSTRAINT ck_prescription_items_qty CHECK (quantity IS NULL OR quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
