package com.medicare.enums;

/**
 * Groups notification types for e-mail preferences. SECURITY e-mails are always sent; STAFF notifications are
 * in-app only and never e-mailed.
 */
public enum EmailCategory {
    APPOINTMENTS, REMINDERS, RESULTS, BILLING, SECURITY, STAFF;

    public static EmailCategory of(NotificationType type) {
        return switch (type) {
            case APPOINTMENT_CONFIRMED, APPOINTMENT_CANCELLED, APPOINTMENT_RESCHEDULED -> APPOINTMENTS;
            case APPOINTMENT_REMINDER, FOLLOW_UP_REMINDER -> REMINDERS;
            case PRESCRIPTION_FINALIZED, LAB_REPORT_READY -> RESULTS;
            case INVOICE_GENERATED, PAYMENT_RECORDED -> BILLING;
            case REGISTRATION, ACCOUNT -> SECURITY;
            case OPERATIONAL -> STAFF;
        };
    }

    /** Categories a user can switch off. */
    public boolean isOptional() {
        return this == APPOINTMENTS || this == REMINDERS || this == RESULTS || this == BILLING;
    }
}
