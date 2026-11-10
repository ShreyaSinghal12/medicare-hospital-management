package com.medicare.enums;

/**
 * Queue status, always derived from the appointment:
 * CHECKED_IN → WAITING, IN_PROGRESS → IN_CONSULTATION, COMPLETED → COMPLETED,
 * CANCELLED / NO_SHOW → SKIPPED. Reception may also skip a waiting patient who did not answer
 * the call (and put them back later).
 */
public enum QueueStatus {
    WAITING,
    IN_CONSULTATION,
    COMPLETED,
    SKIPPED
}
