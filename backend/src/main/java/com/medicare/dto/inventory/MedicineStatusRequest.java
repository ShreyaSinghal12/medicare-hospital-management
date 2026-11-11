package com.medicare.dto.inventory;

import jakarta.validation.constraints.NotNull;

public record MedicineStatusRequest(@NotNull Boolean active) {
}
