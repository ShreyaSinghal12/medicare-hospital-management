package com.medicare.service;

import java.time.ZoneId;

import com.medicare.dto.HospitalInfoResponse;
import com.medicare.entity.HospitalSettings;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.repository.DepartmentRepository;
import com.medicare.repository.HospitalSettingsRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to the single hospital configuration row. Editing is added in
 * the administration phase.
 */
@Service
public class HospitalSettingsService {

    private final HospitalSettingsRepository settingsRepository;
    private final DepartmentRepository departmentRepository;

    public HospitalSettingsService(HospitalSettingsRepository settingsRepository,
                                   DepartmentRepository departmentRepository) {
        this.settingsRepository = settingsRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public HospitalSettings getSettings() {
        return settingsRepository.findById(HospitalSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Hospital settings have not been initialised"));
    }

    /** The hospital's local time zone; all appointment dates/times are interpreted in it. */
    @Transactional(readOnly = true)
    public ZoneId getZoneId() {
        return ZoneId.of(getSettings().getTimeZone());
    }

    @Transactional(readOnly = true)
    public HospitalInfoResponse getPublicInfo() {
        HospitalSettings s = getSettings();
        var departments = departmentRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(d -> new HospitalInfoResponse.DepartmentSummary(d.getId(), d.getName(), d.getDescription()))
                .toList();
        return new HospitalInfoResponse(s.getHospitalName(), s.getAddress(), s.getPhone(), s.getEmail(),
                s.getTimeZone(), s.getCancellationPolicy(), departments);
    }

    /** Reminder lead time and whether reminders are also e-mailed (full settings editing comes with the admin settings screen). */
    @Transactional
    public HospitalSettings updateReminders(boolean emailRemindersEnabled, int reminderHoursBefore, Long actorUserId) {
        HospitalSettings s = getSettings();
        s.setEmailRemindersEnabled(emailRemindersEnabled);
        s.setReminderHoursBefore(reminderHoursBefore);
        s.setUpdatedByUserId(actorUserId);
        return settingsRepository.saveAndFlush(s);
    }
}
