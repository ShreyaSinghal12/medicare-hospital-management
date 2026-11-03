package com.medicare.dto.schedule;

import jakarta.validation.constraints.NotNull;

public record LeaveDecisionRequest(@NotNull Boolean approve) {
}
