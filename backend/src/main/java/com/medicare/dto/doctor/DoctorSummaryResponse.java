package com.medicare.dto.doctor;

import java.math.BigDecimal;

/** Public doctor card (no contact details, no licence number). */
public record DoctorSummaryResponse(
        Long id,
        String fullName,
        Long departmentId,
        String departmentName,
        String specialization,
        String qualifications,
        int experienceYears,
        BigDecimal consultationFee,
        int slotDurationMinutes) {
}
