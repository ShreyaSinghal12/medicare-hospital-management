package com.medicare.dto.admin;

import java.time.Instant;

import com.medicare.enums.ReminderStatus;
import com.medicare.enums.ReminderType;

public record ReminderLogEntry(Long id, ReminderType type, Long appointmentId, Long consultationId, ReminderStatus status,
                               Instant scheduledFor, Instant sentAt, String failureReason) {
}
