package com.medicare.dto.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
        @NotNull Boolean active,
        @Size(max = 300) String reason) {
}
