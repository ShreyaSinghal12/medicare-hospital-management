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

    /**
     * Updates all editable settings (not the time zone) and returns the names of the fields that changed, for the
     * audit log.
     */
    @Transactional
    public java.util.List<String> update(com.medicare.dto.admin.HospitalSettingsRequest r, Long actorUserId) {
        HospitalSettings s = getSettings();
        java.util.List<String> changed = new java.util.ArrayList<>();
        String address = blankToNull(r.address());
        String phone = blankToNull(r.phone());
        String email = blankToNull(r.email());
        String policy = blankToNull(r.cancellationPolicy());
        if (!java.util.Objects.equals(s.getHospitalName(), r.hospitalName().trim())) { changed.add("hospitalName"); s.setHospitalName(r.hospitalName().trim()); }
        if (!java.util.Objects.equals(s.getAddress(), address)) { changed.add("address"); s.setAddress(address); }
        if (!java.util.Objects.equals(s.getPhone(), phone)) { changed.add("phone"); s.setPhone(phone); }
        if (!java.util.Objects.equals(s.getEmail(), email)) { changed.add("email"); s.setEmail(email); }
        if (s.getDefaultSlotMinutes() != r.defaultSlotMinutes()) { changed.add("defaultSlotMinutes"); s.setDefaultSlotMinutes(r.defaultSlotMinutes()); }
        if (s.getBookingWindowDays() != r.bookingWindowDays()) { changed.add("bookingWindowDays"); s.setBookingWindowDays(r.bookingWindowDays()); }
        if (s.getCancellationWindowHours() != r.cancellationWindowHours()) { changed.add("cancellationWindowHours"); s.setCancellationWindowHours(r.cancellationWindowHours()); }
        if (!java.util.Objects.equals(s.getCancellationPolicy(), policy)) { changed.add("cancellationPolicy"); s.setCancellationPolicy(policy); }
        if (!s.getInvoicePrefix().equals(r.invoicePrefix())) { changed.add("invoicePrefix"); s.setInvoicePrefix(r.invoicePrefix()); }
        if (s.getTaxRatePercent().compareTo(r.taxRatePercent()) != 0) { changed.add("taxRatePercent"); s.setTaxRatePercent(r.taxRatePercent()); }
        if (s.isEmailRemindersEnabled() != r.emailRemindersEnabled()) { changed.add("emailRemindersEnabled"); s.setEmailRemindersEnabled(r.emailRemindersEnabled()); }
        if (s.getReminderHoursBefore() != r.reminderHoursBefore()) { changed.add("reminderHoursBefore"); s.setReminderHoursBefore(r.reminderHoursBefore()); }
        if (s.getExpiryWarningDays() != r.expiryWarningDays()) { changed.add("expiryWarningDays"); s.setExpiryWarningDays(r.expiryWarningDays()); }
        if (!changed.isEmpty()) {
            s.setUpdatedByUserId(actorUserId);
            settingsRepository.saveAndFlush(s);
        }
        return changed;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
