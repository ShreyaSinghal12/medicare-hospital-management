package com.medicare.dto.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = "^[\\p{L}0-9 &,.'()\\-]+$", message = "Name contains unsupported characters")
        String name,

        @Size(max = 500)
        String description) {
}
