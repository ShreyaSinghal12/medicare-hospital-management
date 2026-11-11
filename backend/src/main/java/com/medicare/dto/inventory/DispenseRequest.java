package com.medicare.dto.inventory;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Medicines handed out against one finalized prescription; each line names the batch it comes from. */
public record DispenseRequest(
        @NotNull Long prescriptionId,
        @Size(max = 500) String notes,
        @NotEmpty @Size(max = 60) List<@Valid Line> lines) {

    public record Line(@NotNull Long prescriptionItemId, @NotNull Long batchId, @NotNull @Min(1) @Max(10000) Integer quantity) {
    }
}
