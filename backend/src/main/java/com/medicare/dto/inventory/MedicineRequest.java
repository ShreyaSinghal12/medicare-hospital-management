package com.medicare.dto.inventory;

import java.math.BigDecimal;

import com.medicare.enums.MedicineCategory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MedicineRequest(
        @NotBlank @Size(min = 2, max = 150) String name,
        @Size(max = 150) String genericName,
        @NotBlank @Size(max = 150) String manufacturer,
        @NotNull MedicineCategory category,
        @NotBlank @Size(max = 50) String strength,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice,
        @NotNull @Min(0) @Max(100000) Integer reorderLevel) {
}
