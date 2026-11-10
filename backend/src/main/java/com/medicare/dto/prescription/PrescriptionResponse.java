package com.medicare.dto.prescription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.medicare.enums.PrescriptionStatus;

public record PrescriptionResponse(
        Long id,
        String prescriptionNo,
        int versionNumber,
        PrescriptionStatus status,
        Long previousVersionId,
        String amendmentReason,
        Long consultationId,
        Long appointmentId,
        LocalDate consultationDate,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        String specialization,
        String departmentName,
        String generalInstructions,
        List<PrescriptionItemDto> items,
        Instant finalizedAt,
        Instant createdAt,
        long version,
        boolean canEdit,
        boolean canRevise) {
}
