package com.medicare.dto.clinical;

import java.time.Instant;

import com.medicare.enums.AllergySeverity;

public record AllergyResponse(
        Long id,
        String allergen,
        String reaction,
        AllergySeverity severity,
        boolean active,
        String recordedBy,
        Instant recordedAt) {
}
