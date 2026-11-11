package com.medicare.dto.lab;

import java.util.List;

import com.medicare.enums.LabPriority;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A doctor's order: which catalog tests, how urgent, and an optional clinical indication typed by the doctor. */
public record CreateLabOrderRequest(
        @NotNull Long consultationId,
        @NotEmpty @Size(max = 20) List<@NotNull Long> testIds,
        LabPriority priority,
        @Size(max = 1000) String clinicalNotes) {
}
