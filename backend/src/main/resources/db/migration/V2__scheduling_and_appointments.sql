-- =====================================================================
-- V2: Doctor schedules, leave, slot reservations, appointments, queue
-- Appointment dates/times are LOCAL to the hospital time zone.
-- =====================================================================

-- Weekly recurring working hours. day_of_week: 1 = Monday ... 7 = Sunday (ISO-8601).
CREATE TABLE doctor_availability (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    doctor_id              BIGINT      NOT NULL,
    day_of_week            TINYINT     NOT NULL,
    start_time             TIME        NOT NULL,
    end_time               TIME        NOT NULL,
    break_start            TIME,
    break_end              TIME,
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_availability_doctor_day UNIQUE (doctor_id, day_of_week),
    CONSTRAINT fk_availability_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_availability_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_availability_times CHECK (end_time > start_time),
    CONSTRAINT ck_availability_break CHECK (
        (break_start IS NULL AND break_end IS NULL)
        OR (break_start IS NOT NULL AND break_end IS NOT NULL AND break_end > break_start
            AND break_start >= start_time AND break_end <= end_time))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Leave (full days) and special unavailability (partial day: start_time/end_time set).
CREATE TABLE doctor_leave (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    doctor_id            BIGINT       NOT NULL,
    leave_type           VARCHAR(20)  NOT NULL,
    start_date           DATE         NOT NULL,
    end_date             DATE         NOT NULL,
    start_time           TIME,
    end_time             TIME,
    reason               VARCHAR(500),
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by_user_id  BIGINT,
    reviewed_at          DATETIME(6),
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_leave_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT fk_leave_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_leave_type CHECK (leave_type IN ('LEAVE', 'UNAVAILABLE')),
    CONSTRAINT ck_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_leave_dates CHECK (end_date >= start_date),
    INDEX idx_leave_doctor_dates (doctor_id, start_date, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Reservation ledger. Free slots are DERIVED from availability; a row exists here
-- only once a slot is reserved (BOOKED) or blocked. The unique key is the
-- database-level guarantee that a doctor's slot can be held by one booking only.
CREATE TABLE appointment_slots (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    doctor_id   BIGINT      NOT NULL,
    slot_date   DATE        NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version     BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_slots_doctor_date_time UNIQUE (doctor_id, slot_date, start_time),
    CONSTRAINT fk_slots_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_slots_status CHECK (status IN ('AVAILABLE', 'BOOKED', 'BLOCKED')),
    CONSTRAINT ck_slots_times CHECK (end_time > start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE appointments (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    reference_no             VARCHAR(30)  NOT NULL,
    patient_id               BIGINT       NOT NULL,
    doctor_id                BIGINT       NOT NULL,
    slot_id                  BIGINT,
    appointment_date         DATE         NOT NULL,
    start_time               TIME         NOT NULL,
    end_time                 TIME         NOT NULL,
    appointment_type         VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    reason                   VARCHAR(500) NOT NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    needs_reschedule         BOOLEAN      NOT NULL DEFAULT FALSE,
    follow_up_of_id          BIGINT,
    booked_by_user_id        BIGINT       NOT NULL,
    cancelled_by_user_id     BIGINT,
    cancellation_reason      VARCHAR(500),
    cancelled_at             DATETIME(6),
    checked_in_at            DATETIME(6),
    started_at               DATETIME(6),
    completed_at             DATETIME(6),
    reschedule_count         INT          NOT NULL DEFAULT 0,
    created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version                  BIGINT       NOT NULL DEFAULT 0,
    -- Second, independent guard: two non-cancelled appointments can never share a doctor's start time.
    active_doctor_guard      BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'CANCELLED' THEN NULL ELSE doctor_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_appointments_reference UNIQUE (reference_no),
    CONSTRAINT uk_appointments_active_slot UNIQUE (active_doctor_guard, appointment_date, start_time),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients (id),
    CONSTRAINT fk_appointments_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT fk_appointments_slot FOREIGN KEY (slot_id) REFERENCES appointment_slots (id),
    CONSTRAINT fk_appointments_follow_up FOREIGN KEY (follow_up_of_id) REFERENCES appointments (id),
    CONSTRAINT fk_appointments_booked_by FOREIGN KEY (booked_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_cancelled_by FOREIGN KEY (cancelled_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_appointments_status CHECK (status IN ('SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_appointments_type CHECK (appointment_type IN ('NEW', 'FOLLOW_UP', 'WALK_IN')),
    CONSTRAINT ck_appointments_times CHECK (end_time > start_time),
    INDEX idx_appointments_doctor_date (doctor_id, appointment_date),
    INDEX idx_appointments_patient_date (patient_id, appointment_date),
    INDEX idx_appointments_status_date (status, appointment_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every status change / reschedule is recorded here (appointment rows are updated in place).
CREATE TABLE appointment_status_history (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    appointment_id       BIGINT       NOT NULL,
    from_status          VARCHAR(20),
    to_status            VARCHAR(20)  NOT NULL,
    action               VARCHAR(30)  NOT NULL,
    details              VARCHAR(500),
    changed_by_user_id   BIGINT,
    changed_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_appt_history_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_appt_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id),
    INDEX idx_appt_history_appointment (appointment_id, changed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE queue_entries (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_id  BIGINT      NOT NULL,
    doctor_id       BIGINT      NOT NULL,
    queue_date      DATE        NOT NULL,
    token_number    INT         NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    checked_in_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    called_at       DATETIME(6),
    completed_at    DATETIME(6),
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version         BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_queue_appointment UNIQUE (appointment_id),
    CONSTRAINT uk_queue_doctor_date_token UNIQUE (doctor_id, queue_date, token_number),
    CONSTRAINT fk_queue_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_queue_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    CONSTRAINT ck_queue_status CHECK (status IN ('WAITING', 'IN_CONSULTATION', 'COMPLETED', 'SKIPPED')),
    INDEX idx_queue_date_status (queue_date, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
