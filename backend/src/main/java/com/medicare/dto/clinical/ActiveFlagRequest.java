package com.medicare.dto.clinical;

import jakarta.validation.constraints.NotNull;

public record ActiveFlagRequest(@NotNull Boolean active) {
}
