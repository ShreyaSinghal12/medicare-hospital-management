package com.medicare.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.admin.AdminDashboard;
import com.medicare.dto.admin.HospitalSettingsRequest;
import com.medicare.dto.admin.HospitalSettingsResponse;
import com.medicare.enums.AuditOutcome;
import com.medicare.security.CurrentUser;
import com.medicare.service.AnalyticsService;
import com.medicare.service.AuditService;
import com.medicare.service.HospitalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator dashboard statistics and hospital settings. */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: dashboard and settings")
public class AdminDashboardController {

    private final AnalyticsService analyticsService;
    private final HospitalSettingsService settingsService;
    private final AuditService auditService;

    public AdminDashboardController(AnalyticsService analyticsService, HospitalSettingsService settingsService, AuditService auditService) {
        this.analyticsService = analyticsService;
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    @GetMapping("/api/admin/dashboard")
    @Operation(summary = "Statistics for a date range (default: last 30 days; at most 366 days)")
    public AdminDashboard dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analyticsService.dashboard(from, to);
    }

    @GetMapping("/api/admin/settings")
    public HospitalSettingsResponse settings() {
        var s = settingsService.getSettings();
        return new HospitalSettingsResponse(s.getHospitalName(), s.getAddress(), s.getPhone(), s.getEmail(), s.getTimeZone(),
                s.getDefaultSlotMinutes(), s.getBookingWindowDays(), s.getCancellationWindowHours(), s.getCancellationPolicy(),
                s.getInvoicePrefix(), s.getTaxRatePercent(), s.isEmailRemindersEnabled(), s.getReminderHoursBefore(),
                s.getExpiryWarningDays(), s.getUpdatedAt());
    }

    @PutMapping("/api/admin/settings")
    @Operation(summary = "Update hospital settings (the time zone is fixed at installation)")
    public HospitalSettingsResponse update(@Valid @RequestBody HospitalSettingsRequest request) {
        List<String> changed = settingsService.update(request, CurrentUser.id());
        if (!changed.isEmpty()) {
            auditService.record("SETTINGS_UPDATED", "HospitalSettings", 1, AuditOutcome.SUCCESS, "Changed: " + String.join(", ", changed));
        }
        return settings();
    }
}
