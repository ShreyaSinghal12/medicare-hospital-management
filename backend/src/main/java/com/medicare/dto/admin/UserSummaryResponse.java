package com.medicare.dto.admin;

import java.time.Instant;

import com.medicare.enums.Role;

/** Non-clinical account information shown in admin user management. */
public record UserSummaryResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean active,
        boolean locked,
        Instant lastLoginAt,
        Instant createdAt) {
}
