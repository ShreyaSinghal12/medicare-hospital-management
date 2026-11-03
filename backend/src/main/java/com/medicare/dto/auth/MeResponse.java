package com.medicare.dto.auth;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medicare.enums.Role;

/**
 * The logged-in user as seen by the frontend (GET /api/auth/me, POST /api/auth/login).
 * Role-specific ids are included only for the matching role.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean emailNotificationsEnabled,
        Instant lastLoginAt,
        Long patientId,
        String patientCode,
        Long doctorId,
        String departmentName,
        String employeeCode) {
}
