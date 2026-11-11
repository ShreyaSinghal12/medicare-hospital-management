package com.medicare.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** How many hours before an appointment the reminder goes out, and whether reminders are also e-mailed. */
public record ReminderSettings(@NotNull Boolean emailRemindersEnabled, @NotNull @Min(1) @Max(168) Integer reminderHoursBefore) {
}
