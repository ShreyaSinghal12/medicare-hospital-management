package com.medicare.dto.staff;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medicare.enums.Role;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StaffResponse(
        Long userId,
        Role role,
        String fullName,
        String email,
        String phone,
        boolean active,
        String employeeCode,
        String qualification,
        String licenseNumber,
        Instant lastLoginAt,
        Instant createdAt) {
}
