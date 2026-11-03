package com.medicare.dto.clinical;

import java.time.Instant;
import java.time.LocalDate;

import com.medicare.enums.ConditionStatus;

public record ConditionResponse(
        Long id,
        String conditionName,
        ConditionStatus status,
        LocalDate diagnosedOn,
        String notes,
        String recordedBy,
        Instant recordedAt) {
}
