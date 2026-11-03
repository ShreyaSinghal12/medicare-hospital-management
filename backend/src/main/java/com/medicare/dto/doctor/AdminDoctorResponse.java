package com.medicare.dto.doctor;

import java.math.BigDecimal;
import java.time.Instant;

/** Doctor as seen by administrators (account + professional data, no clinical data). */
public record AdminDoctorResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String phone,
        boolean active,
        Long departmentId,
        String departmentName,
        String specialization,
        String qualifications,
        String licenseNumber,
        int experienceYears,
        BigDecimal consultationFee,
        int slotDurationMinutes,
        String bio,
        Instant lastLoginAt,
        Instant createdAt) {
}
