package com.medicare.dto.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.medicare.enums.PrescriptionStatus;

/** List row: number, version, status, who/when and the medicine names. */
public record PrescriptionSummary(
        Long id,
        String prescriptionNo,
        int versionNumber,
        PrescriptionStatus status,
        LocalDate consultationDate,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        String specialization,
        List<String> medicines,
        Instant finalizedAt,
        Instant createdAt) {
}
