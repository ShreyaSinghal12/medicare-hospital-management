-- =====================================================================
-- V14: Per-category e-mail preferences.
-- A missing row means "enabled". Security e-mails (password reset, account
-- changes) have no row and are always sent. In-app notifications are
-- always created regardless of these settings.
-- =====================================================================

CREATE TABLE notification_email_preferences (
    user_id     BIGINT      NOT NULL,
    category    VARCHAR(20) NOT NULL,
    enabled     BOOLEAN     NOT NULL,
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, category),
    CONSTRAINT fk_email_prefs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_email_prefs_category CHECK (category IN ('APPOINTMENTS', 'REMINDERS', 'RESULTS', 'BILLING'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
