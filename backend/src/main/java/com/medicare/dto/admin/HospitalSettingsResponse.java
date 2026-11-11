package com.medicare.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record HospitalSettingsResponse(
        String hospitalName,
        String address,
        String phone,
        String email,
        String timeZone,
        int defaultSlotMinutes,
        int bookingWindowDays,
        int cancellationWindowHours,
        String cancellationPolicy,
        String invoicePrefix,
        BigDecimal taxRatePercent,
        boolean emailRemindersEnabled,
        int reminderHoursBefore,
        int expiryWarningDays,
        Instant updatedAt) {
}
