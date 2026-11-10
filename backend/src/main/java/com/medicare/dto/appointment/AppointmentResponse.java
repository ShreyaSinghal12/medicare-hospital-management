package com.medicare.dto.appointment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.QueueStatus;

/**
 * Appointment as seen by an authorised viewer. {@code reason} (free text from the patient) is
 * withheld from receptionists and administrators. {@code canCancel}/{@code canReschedule}
 * tell the UI which actions the current user may perform right now.
 */
public record AppointmentResponse(
        Long id,
        String referenceNo,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        String specialization,
        String departmentName,
        BigDecimal consultationFee,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        AppointmentType type,
        String reason,
        AppointmentStatus status,
        boolean needsReschedule,
        Long followUpOfId,
        int rescheduleCount,
        String cancellationReason,
        Instant cancelledAt,
        Instant createdAt,
        Integer queueToken,
        QueueStatus queueStatus,
        boolean canCancel,
        boolean canReschedule) {
}
