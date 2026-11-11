package com.medicare.dto.lab;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Result values typed by the ordering doctor. The system stores them as entered and never interprets them. */
public record LabResultsRequest(
        Long version,
        @NotEmpty @Size(max = 20) List<@Valid Entry> items) {

    public record Entry(@NotNull Long itemId, @Size(max = 100) String resultValue, @Size(max = 500) String resultComment) {
    }
}
