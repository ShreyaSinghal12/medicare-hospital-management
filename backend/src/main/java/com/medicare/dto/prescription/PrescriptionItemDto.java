package com.medicare.dto.prescription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One medicine line as typed by the doctor. Nothing is suggested or auto-filled by the system. */
public record PrescriptionItemDto(
        @NotBlank @Size(max = 150) String medicineName,
        @Size(max = 50) String strength,
        @NotBlank @Size(max = 100) String dosage,
        @NotBlank @Size(max = 100) String frequency,
        @NotBlank @Size(max = 100) String duration,
        @Size(max = 500) String instructions,
        @Min(1) @Max(10000) Integer quantity) {
}
