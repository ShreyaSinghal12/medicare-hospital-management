-- =====================================================================
-- V7: Notifications, appointment/follow-up reminders, audit logs
-- =====================================================================

-- idempotency_key (e.g. "APPT_REMINDER:42:UPCOMING") makes repeated
-- scheduler runs or retried requests unable to create duplicates.
CREATE TABLE notifications (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    type             VARCHAR(40)   NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    message          VARCHAR(1000) NOT NULL,
    link             VARCHAR(255),
    is_read          BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at          DATETIME(6),
    idempotency_key  VARCHAR(120),
    email_status     VARCHAR(20)   NOT NULL DEFAULT 'NOT_REQUESTED',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_notifications_email CHECK (email_status IN ('NOT_REQUESTED', 'PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    INDEX idx_notifications_user_read (user_id, is_read, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE appointment_reminders (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    reminder_type    VARCHAR(20)  NOT NULL,
    appointment_id   BIGINT,
    consultation_id  BIGINT,
    scheduled_for    DATETIME(6)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    idempotency_key  VARCHAR(120) NOT NULL,
    sent_at          DATETIME(6),
    failure_reason   VARCHAR(255),
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_reminders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_reminders_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_reminders_consultation FOREIGN KEY (consultation_id) REFERENCES consultations (id),
    CONSTRAINT ck_reminders_type CHECK (reminder_type IN ('UPCOMING', 'FOLLOW_UP')),
    CONSTRAINT ck_reminders_status CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    CONSTRAINT ck_reminders_target CHECK (appointment_id IS NOT NULL OR consultation_id IS NOT NULL),
    INDEX idx_reminders_due (status, scheduled_for)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- No passwords, tokens or clinical free text are ever written to details.
CREATE TABLE audit_logs (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT,
    actor_email    VARCHAR(150),
    actor_role     VARCHAR(20),
    action         VARCHAR(60)   NOT NULL,
    entity_type    VARCHAR(50),
    entity_id      VARCHAR(50),
    outcome        VARCHAR(10)   NOT NULL DEFAULT 'SUCCESS',
    details        VARCHAR(1000),
    ip_address     VARCHAR(45),
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_actor (actor_user_id, created_at),
    INDEX idx_audit_action (action, created_at),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
