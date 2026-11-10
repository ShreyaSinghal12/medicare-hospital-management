package com.medicare.dto.staff;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medicare.enums.Role;

/** Receptionist or administrator account. {@code employeeCode} is set for receptionists. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffResponse(
        Long userId,
        Role role,
        String fullName,
        String email,
        String phone,
        boolean active,
        String employeeCode,
        Instant lastLoginAt,
        Instant createdAt) {
}
