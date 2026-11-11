package com.medicare.entity;

import java.time.Instant;

import com.medicare.enums.ReminderStatus;
import com.medicare.enums.ReminderType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One reminder that was claimed for sending. The unique {@code idempotencyKey} guarantees a reminder is sent at most
 * once, even when several application instances run the scheduler at the same time.
 */
@Entity
@Table(name = "appointment_reminders")
public class AppointmentReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 20)
    private ReminderType type;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "consultation_id")
    private Long consultationId;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AppointmentReminder() {
        // for JPA
    }

    public AppointmentReminder(ReminderType type, Long appointmentId, Long consultationId, Instant scheduledFor, String key) {
        this.type = type;
        this.appointmentId = appointmentId;
        this.consultationId = consultationId;
        this.scheduledFor = scheduledFor;
        this.idempotencyKey = key;
    }

    public void markSent() {
        status = ReminderStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed(String reason) {
        status = ReminderStatus.FAILED;
        failureReason = reason.length() > 255 ? reason.substring(0, 255) : reason;
    }

    public Long getId() { return id; }
    public ReminderType getType() { return type; }
    public Long getAppointmentId() { return appointmentId; }
    public Long getConsultationId() { return consultationId; }
    public Instant getScheduledFor() { return scheduledFor; }
    public ReminderStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getSentAt() { return sentAt; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
