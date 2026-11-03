package com.medicare.enums;

/** Every kind of in-app notification the system can raise. */
public enum NotificationType {
    REGISTRATION,
    ACCOUNT,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_REMINDER,
    FOLLOW_UP_REMINDER,
    PRESCRIPTION_FINALIZED,
    LAB_REPORT_READY,
    INVOICE_GENERATED,
    PAYMENT_RECORDED,
    OPERATIONAL
}
