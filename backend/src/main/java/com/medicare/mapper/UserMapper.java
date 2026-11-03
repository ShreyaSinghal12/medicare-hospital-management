package com.medicare.mapper;

import java.time.Instant;

import com.medicare.dto.admin.UserSummaryResponse;
import com.medicare.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserSummaryResponse toSummary(User u) {
        return new UserSummaryResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(),
                u.isActive(), u.isLocked(Instant.now()), u.getLastLoginAt(), u.getCreatedAt());
    }
}
