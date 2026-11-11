package com.medicare.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A received delivery: one new batch. */
public record StockInRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9/._-]{0,49}$", message = "Letters, digits and / . _ - only (max 50)")
        String batchNumber,
        @NotNull LocalDate expiryDate,
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 8, fraction = 2) BigDecimal purchasePrice,
        @Size(max = 150) String supplier,
        @Size(max = 100) String reference) {
}
