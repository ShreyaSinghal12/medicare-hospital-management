package com.medicare.dto.clinical;

import java.time.LocalDate;

import com.medicare.enums.ConditionStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record ConditionRequest(
        @NotBlank @Size(max = 150) String conditionName,
        @NotNull ConditionStatus status,
        @PastOrPresent LocalDate diagnosedOn,
        @Size(max = 500) String notes) {
}
