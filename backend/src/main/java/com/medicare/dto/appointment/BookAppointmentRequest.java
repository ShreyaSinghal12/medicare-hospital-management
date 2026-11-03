package com.medicare.dto.appointment;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Book an appointment. Patients book for themselves ({@code patientId} must be empty);
 * receptionists and administrators must give {@code patientId}.
 */
public record BookAppointmentRequest(
        @NotNull Long doctorId,
        Long patientId,
        @NotNull LocalDate date,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotBlank @Size(min = 3, max = 500) String reason,
        Long followUpOfId) {
}
