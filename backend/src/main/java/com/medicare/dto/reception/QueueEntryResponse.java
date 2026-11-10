package com.medicare.dto.reception;

import java.time.Instant;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.QueueStatus;

/** Queue board row (scheduling data only, no clinical content). */
public record QueueEntryResponse(
        Long id,
        int tokenNumber,
        QueueStatus status,
        Long appointmentId,
        String appointmentReference,
        AppointmentStatus appointmentStatus,
        @JsonFormat(pattern = "HH:mm") LocalTime appointmentTime,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        Instant checkedInAt,
        Instant calledAt,
        Instant completedAt) {
}
