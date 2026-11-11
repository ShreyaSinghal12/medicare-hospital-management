package com.medicare.dto.lab;

import jakarta.validation.constraints.NotNull;

public record LabTestStatusRequest(@NotNull Boolean active) {
}
