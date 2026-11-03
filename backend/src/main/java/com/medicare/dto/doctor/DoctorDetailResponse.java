package com.medicare.dto.doctor;

import java.math.BigDecimal;

/** Public doctor profile page. Working days and free slots are added with scheduling (Phase 4). */
public record DoctorDetailResponse(
        Long id,
        String fullName,
        Long departmentId,
        String departmentName,
        String specialization,
        String qualifications,
        int experienceYears,
        BigDecimal consultationFee,
        int slotDurationMinutes,
        String bio) {
}
