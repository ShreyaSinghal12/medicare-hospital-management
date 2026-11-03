package com.medicare.dto.clinical;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicare.enums.AppointmentStatus;

/** Appointment line (scheduling data only) inside a patient record. */
public record VisitSummary(
        Long appointmentId,
        String referenceNo,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        String doctorName,
        String departmentName,
        AppointmentStatus status) {
}
