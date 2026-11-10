package com.medicare.dto.prescription;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create (with {@code consultationId}) or update a draft. {@code version} must be the value the
 * client loaded when updating; a stale value returns 409.
 */
public record PrescriptionRequest(
        Long consultationId,
        @NotNull @Size(min = 1, max = 20, message = "Add between 1 and 20 medicines") List<@Valid PrescriptionItemDto> items,
        @Size(max = 1000) String generalInstructions,
        Long version) {
}
