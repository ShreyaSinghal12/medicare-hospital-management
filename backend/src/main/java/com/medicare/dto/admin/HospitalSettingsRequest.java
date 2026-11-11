package com.medicare.dto.admin;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Editable hospital settings. The time zone is not editable here: changing it would shift the meaning of every
 * stored appointment time, so it is fixed at installation.
 */
public record HospitalSettingsRequest(
        @NotBlank @Size(min = 2, max = 150) String hospitalName,
        @Size(max = 500) String address,
        @Size(max = 30) @Pattern(regexp = "^$|^[+0-9 ()-]{6,30}$", message = "Enter a valid phone number") String phone,
        @Size(max = 150) @Email String email,
        @NotNull @Min(5) @Max(120) Integer defaultSlotMinutes,
        @NotNull @Min(1) @Max(365) Integer bookingWindowDays,
        @NotNull @Min(0) @Max(168) Integer cancellationWindowHours,
        @Size(max = 1000) String cancellationPolicy,
        @NotBlank @Pattern(regexp = "^[A-Z]{2,6}$", message = "2–6 capital letters") String invoicePrefix,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal taxRatePercent,
        @NotNull Boolean emailRemindersEnabled,
        @NotNull @Min(1) @Max(168) Integer reminderHoursBefore,
        @NotNull @Min(1) @Max(365) Integer expiryWarningDays) {
}
