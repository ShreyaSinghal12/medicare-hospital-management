package com.medicare.dto.clinical;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.ConsultationStatus;

/**
 * Consultation as seen by the viewer. For patients, the clinician-facing fields
 * {@code observations} and {@code notes} are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConsultationResponse(
        Long id,
        Long appointmentId,
        String appointmentReference,
        AppointmentStatus appointmentStatus,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        String visitReason,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        String specialization,
        String departmentName,
        String symptoms,
        String observations,
        String diagnosis,
        String treatmentPlan,
        String notes,
        LocalDate followUpDate,
        String followUpNotes,
        ConsultationStatus status,
        Instant finalizedAt,
        int amendmentCount,
        long version,
        Instant updatedAt,
        boolean canEdit,
        boolean canAmend) {
}
