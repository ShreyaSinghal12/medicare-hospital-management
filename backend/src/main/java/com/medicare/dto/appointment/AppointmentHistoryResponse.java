package com.medicare.dto.appointment;

import java.time.Instant;

import com.medicare.enums.AppointmentStatus;

public record AppointmentHistoryResponse(
        AppointmentStatus fromStatus,
        AppointmentStatus toStatus,
        String action,
        String details,
        Instant changedAt) {
}
