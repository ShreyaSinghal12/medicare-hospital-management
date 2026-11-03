package com.medicare.dto.clinical;

import com.medicare.enums.ConditionStatus;

import jakarta.validation.constraints.NotNull;

public record ConditionStatusRequest(@NotNull ConditionStatus status) {
}
