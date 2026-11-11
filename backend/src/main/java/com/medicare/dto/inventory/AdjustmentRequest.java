package com.medicare.dto.inventory;

import com.medicare.enums.InventoryTxnType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** ADJUSTMENT_IN, ADJUSTMENT_OUT (e.g. damaged, count correction) or STOCK_OUT (returned to supplier). */
public record AdjustmentRequest(
        @NotNull InventoryTxnType type,
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @NotBlank @Size(min = 3, max = 500) String reason) {
}
