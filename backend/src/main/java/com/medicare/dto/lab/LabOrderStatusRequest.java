package com.medicare.dto.lab;

import com.medicare.enums.LabOrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** SAMPLE_COLLECTED, PROCESSING or CANCELLED (a reason is required to cancel). */
public record LabOrderStatusRequest(@NotNull LabOrderStatus status, Long version, @Size(max = 500) String reason) {
}
