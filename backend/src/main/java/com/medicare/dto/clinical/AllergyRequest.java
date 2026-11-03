package com.medicare.dto.clinical;

import com.medicare.enums.AllergySeverity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AllergyRequest(
        @NotBlank @Size(max = 150) String allergen,
        @Size(max = 255) String reaction,
        AllergySeverity severity) {
}
