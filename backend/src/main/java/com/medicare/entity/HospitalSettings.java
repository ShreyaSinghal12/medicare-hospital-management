package com.medicare.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row hospital configuration (primary key is always 1).
 * Maps to table {@code hospital_settings} created in V1.
 */
@Entity
@Table(name = "hospital_settings")
public class HospitalSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "hospital_name", nullable = false, length = 150)
    private String hospitalName;

    @Column(length = 500)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone;

    @Column(name = "default_slot_minutes", nullable = false)
    private int defaultSlotMinutes;

    @Column(name = "booking_window_days", nullable = false)
    private int bookingWindowDays;

    @Column(name = "cancellation_window_hours", nullable = false)
    private int cancellationWindowHours;

    @Column(name = "cancellation_policy", length = 1000)
    private String cancellationPolicy;

    @Column(name = "invoice_prefix", nullable = false, length = 10)
    private String invoicePrefix;

    @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRatePercent;

    @Column(name = "email_reminders_enabled", nullable = false)
    private boolean emailRemindersEnabled;

    @Column(name = "reminder_hours_before", nullable = false)
    private int reminderHoursBefore;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    protected HospitalSettings() {
        // for JPA
    }

    public Long getId() { return id; }
    public String getHospitalName() { return hospitalName; }
    public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public int getDefaultSlotMinutes() { return defaultSlotMinutes; }
    public void setDefaultSlotMinutes(int defaultSlotMinutes) { this.defaultSlotMinutes = defaultSlotMinutes; }
    public int getBookingWindowDays() { return bookingWindowDays; }
    public void setBookingWindowDays(int bookingWindowDays) { this.bookingWindowDays = bookingWindowDays; }
    public int getCancellationWindowHours() { return cancellationWindowHours; }
    public void setCancellationWindowHours(int cancellationWindowHours) { this.cancellationWindowHours = cancellationWindowHours; }
    public String getCancellationPolicy() { return cancellationPolicy; }
    public void setCancellationPolicy(String cancellationPolicy) { this.cancellationPolicy = cancellationPolicy; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }
    public BigDecimal getTaxRatePercent() { return taxRatePercent; }
    public void setTaxRatePercent(BigDecimal taxRatePercent) { this.taxRatePercent = taxRatePercent; }
    public boolean isEmailRemindersEnabled() { return emailRemindersEnabled; }
    public void setEmailRemindersEnabled(boolean emailRemindersEnabled) { this.emailRemindersEnabled = emailRemindersEnabled; }
    public int getReminderHoursBefore() { return reminderHoursBefore; }
    public void setReminderHoursBefore(int reminderHoursBefore) { this.reminderHoursBefore = reminderHoursBefore; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(Long updatedByUserId) { this.updatedByUserId = updatedByUserId; }
}
