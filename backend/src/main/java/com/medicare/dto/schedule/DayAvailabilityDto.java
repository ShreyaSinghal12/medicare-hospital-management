package com.medicare.dto.schedule;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Working hours for one ISO day of week (1 = Monday … 7 = Sunday).
 * When {@code working} is false the times are ignored.
 */
public record DayAvailabilityDto(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        boolean working,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @JsonFormat(pattern = "HH:mm") LocalTime breakStart,
        @JsonFormat(pattern = "HH:mm") LocalTime breakEnd) {
}
