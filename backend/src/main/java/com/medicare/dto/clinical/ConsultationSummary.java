package com.medicare.dto.clinical;

import java.time.LocalDate;

import com.medicare.enums.ConsultationStatus;

/** One line in a patient record's consultation list. */
public record ConsultationSummary(
        Long id,
        Long appointmentId,
        LocalDate date,
        String doctorName,
        String specialization,
        String diagnosis,
        ConsultationStatus status,
        int amendmentCount,
        boolean mine) {
}
