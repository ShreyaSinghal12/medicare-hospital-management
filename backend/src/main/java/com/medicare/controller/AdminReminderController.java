package com.medicare.controller;

import java.time.Instant;

import jakarta.validation.Valid;

import com.medicare.dto.admin.ReminderLogEntry;
import com.medicare.dto.admin.ReminderSettings;
import com.medicare.dto.common.PageResponse;
import com.medicare.enums.AuditOutcome;
import com.medicare.notification.ReminderService;
import com.medicare.repository.AppointmentReminderRepository;
import com.medicare.security.CurrentUser;
import com.medicare.service.AuditService;
import com.medicare.service.HospitalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reminder settings, a manual run and the reminder log (administrators). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: reminders")
public class AdminReminderController {

    private final HospitalSettingsService settingsService;
    private final ReminderService reminderService;
    private final AppointmentReminderRepository reminderRepository;
    private final AuditService auditService;

    public AdminReminderController(HospitalSettingsService settingsService, ReminderService reminderService,
                                   AppointmentReminderRepository reminderRepository, AuditService auditService) {
        this.settingsService = settingsService;
        this.reminderService = reminderService;
        this.reminderRepository = reminderRepository;
        this.auditService = auditService;
    }

    @GetMapping("/api/admin/settings/reminders")
    public ReminderSettings settings() {
        var s = settingsService.getSettings();
        return new ReminderSettings(s.isEmailRemindersEnabled(), s.getReminderHoursBefore());
    }

    @PutMapping("/api/admin/settings/reminders")
    public ReminderSettings update(@Valid @RequestBody ReminderSettings request) {
        settingsService.updateReminders(request.emailRemindersEnabled(), request.reminderHoursBefore(), CurrentUser.id());
        auditService.record("REMINDER_SETTINGS_CHANGED", "HospitalSettings", 1, AuditOutcome.SUCCESS,
                "hours=" + request.reminderHoursBefore() + ", email=" + request.emailRemindersEnabled());
        return settings();
    }

    @PostMapping("/api/admin/reminders/run")
    @Operation(summary = "Send due reminders now (normally every 15 minutes); each reminder is still sent only once")
    public ReminderService.RunResult runNow() {
        ReminderService.RunResult r = reminderService.runDue(Instant.now());
        auditService.record("REMINDERS_RUN_MANUALLY", "Reminder", null, AuditOutcome.SUCCESS,
                r.upcomingSent() + " upcoming, " + r.followUpsSent() + " follow-up" + (r.quietHours() ? " (quiet hours)" : ""));
        return r;
    }

    @GetMapping("/api/admin/reminders")
    @Operation(summary = "Recently sent reminders")
    public PageResponse<ReminderLogEntry> log(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(reminderRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100))),
                r -> new ReminderLogEntry(r.getId(), r.getType(), r.getAppointmentId(), r.getConsultationId(), r.getStatus(),
                        r.getScheduledFor(), r.getSentAt(), r.getFailureReason()));
    }
}
