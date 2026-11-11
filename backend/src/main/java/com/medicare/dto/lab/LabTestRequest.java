package com.medicare.dto.lab;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Catalog entry as maintained by an administrator. Unit and reference range are display text only. */
public record LabTestRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,20}$", message = "2–20 letters, digits or hyphens") String code,
        @NotBlank @Size(min = 2, max = 150) String name,
        @Size(max = 500) String description,
        @Size(max = 50) String sampleType,
        @Size(max = 30) String unit,
        @Size(max = 100) String referenceRange,
        @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(1) @Max(720) Integer turnaroundHours) {
}
