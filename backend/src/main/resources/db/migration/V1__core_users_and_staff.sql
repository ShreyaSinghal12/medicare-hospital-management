-- =====================================================================
-- V1: Core identity tables — users, role profiles, departments, settings
-- All timestamps are stored in UTC (DATETIME(6)); the hospital's local
-- time zone lives in hospital_settings.time_zone.
-- =====================================================================

CREATE TABLE departments (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE users (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    email                       VARCHAR(150) NOT NULL,
    password_hash               VARCHAR(100) NOT NULL,
    full_name                   VARCHAR(120) NOT NULL,
    phone                       VARCHAR(20),
    role                        VARCHAR(20)  NOT NULL,
    active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts       INT          NOT NULL DEFAULT 0,
    locked_until                DATETIME(6),
    last_login_at               DATETIME(6),
    email_notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    profile_image_path          VARCHAR(255),
    created_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('PATIENT', 'DOCTOR', 'RECEPTIONIST', 'ADMIN', 'LAB_TECHNICIAN', 'PHARMACIST')),
    INDEX idx_users_role_active (role, active),
    INDEX idx_users_full_name (full_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE patients (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                  BIGINT       NOT NULL,
    patient_code             VARCHAR(20)  NOT NULL,
    date_of_birth            DATE         NOT NULL,
    gender                   VARCHAR(20),
    blood_group              VARCHAR(5),
    address                  VARCHAR(500),
    emergency_contact_name   VARCHAR(120),
    emergency_contact_phone  VARCHAR(20),
    registered_by_user_id    BIGINT,
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_patients_user UNIQUE (user_id),
    CONSTRAINT uk_patients_code UNIQUE (patient_code),
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_patients_registered_by FOREIGN KEY (registered_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_patients_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    CONSTRAINT ck_patients_blood CHECK (blood_group IS NULL OR blood_group IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-')),
    INDEX idx_patients_dob (date_of_birth)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE doctors (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    user_id                BIGINT        NOT NULL,
    department_id          BIGINT        NOT NULL,
    specialization         VARCHAR(100)  NOT NULL,
    qualifications         VARCHAR(255)  NOT NULL,
    license_number         VARCHAR(50)   NOT NULL,
    experience_years       INT           NOT NULL DEFAULT 0,
    consultation_fee       DECIMAL(10, 2) NOT NULL,
    slot_duration_minutes  INT           NOT NULL DEFAULT 15,
    bio                    TEXT,
    created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_doctors_user UNIQUE (user_id),
    CONSTRAINT uk_doctors_license UNIQUE (license_number),
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_doctors_fee CHECK (consultation_fee >= 0),
    CONSTRAINT ck_doctors_slot CHECK (slot_duration_minutes BETWEEN 5 AND 120),
    CONSTRAINT ck_doctors_experience CHECK (experience_years >= 0),
    INDEX idx_doctors_specialization (specialization)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE receptionists (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    employee_code  VARCHAR(20) NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_receptionists_user UNIQUE (user_id),
    CONSTRAINT uk_receptionists_code UNIQUE (employee_code),
    CONSTRAINT fk_receptionists_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE lab_technicians (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    employee_code  VARCHAR(20)  NOT NULL,
    qualification  VARCHAR(150),
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_technicians_user UNIQUE (user_id),
    CONSTRAINT uk_lab_technicians_code UNIQUE (employee_code),
    CONSTRAINT fk_lab_technicians_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE pharmacists (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    employee_code   VARCHAR(20) NOT NULL,
    license_number  VARCHAR(50),
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_pharmacists_user UNIQUE (user_id),
    CONSTRAINT uk_pharmacists_code UNIQUE (employee_code),
    CONSTRAINT fk_pharmacists_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Single-row configuration table (id is always 1).
CREATE TABLE hospital_settings (
    id                          BIGINT        NOT NULL,
    hospital_name               VARCHAR(150)  NOT NULL,
    address                     VARCHAR(500),
    phone                       VARCHAR(30),
    email                       VARCHAR(150),
    time_zone                   VARCHAR(50)   NOT NULL DEFAULT 'Asia/Kolkata',
    default_slot_minutes        INT           NOT NULL DEFAULT 15,
    booking_window_days         INT           NOT NULL DEFAULT 30,
    cancellation_window_hours   INT           NOT NULL DEFAULT 2,
    cancellation_policy         VARCHAR(1000),
    invoice_prefix              VARCHAR(10)   NOT NULL DEFAULT 'INV',
    tax_rate_percent            DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    email_reminders_enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    reminder_hours_before       INT           NOT NULL DEFAULT 24,
    updated_at                  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by_user_id          BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT fk_settings_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_settings_tax CHECK (tax_rate_percent BETWEEN 0 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Concurrency-safe counters for human-readable numbers
-- (patient codes, appointment references, invoice numbers, ...).
-- Rows are locked with SELECT ... FOR UPDATE inside the business transaction.
CREATE TABLE number_sequences (
    sequence_name  VARCHAR(50) NOT NULL,
    next_value     BIGINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (sequence_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,          -- SHA-256 hex of the raw token; raw token is never stored
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6),
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_password_reset_user (user_id, expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
