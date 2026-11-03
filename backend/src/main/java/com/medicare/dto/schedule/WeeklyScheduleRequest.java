package com.medicare.dto.schedule;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the doctor's whole weekly schedule (exactly one entry per day). */
public record WeeklyScheduleRequest(@NotNull @Size(min = 7, max = 7) List<@Valid DayAvailabilityDto> days) {
}
