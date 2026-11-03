package com.medicare.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Appointment life cycle with the only allowed transitions:
 *
 * <pre>
 * SCHEDULED ──check-in──▶ CHECKED_IN ──start──▶ IN_PROGRESS ──complete──▶ COMPLETED
 *     │                       │
 *     ├──cancel──▶ CANCELLED ◀┤
 *     └──no-show─▶ NO_SHOW   ◀┘
 * </pre>
 */
public enum AppointmentStatus {
    SCHEDULED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED = Map.of(
            SCHEDULED, EnumSet.of(CHECKED_IN, CANCELLED, NO_SHOW),
            CHECKED_IN, EnumSet.of(IN_PROGRESS, CANCELLED, NO_SHOW),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(AppointmentStatus.class),
            CANCELLED, EnumSet.noneOf(AppointmentStatus.class),
            NO_SHOW, EnumSet.noneOf(AppointmentStatus.class));

    public boolean canTransitionTo(AppointmentStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Statuses in which the appointment still occupies its time slot (everything except CANCELLED). */
    public boolean occupiesSlot() {
        return this != CANCELLED;
    }

    /** Statuses that are not yet finished. */
    public boolean isOpen() {
        return this == SCHEDULED || this == CHECKED_IN || this == IN_PROGRESS;
    }

    public static Set<AppointmentStatus> open() {
        return EnumSet.of(SCHEDULED, CHECKED_IN, IN_PROGRESS);
    }
}
