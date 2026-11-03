package com.medicare.entity;

import java.time.Instant;

import com.medicare.enums.AppointmentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One row per booking, status change or reschedule of an appointment. */
@Entity
@Table(name = "appointment_status_history")
public class AppointmentStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private AppointmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private AppointmentStatus toStatus;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 500)
    private String details;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AppointmentStatusHistory() {
        // for JPA
    }

    public AppointmentStatusHistory(Long appointmentId, AppointmentStatus from, AppointmentStatus to, String action,
                                    String details, Long changedByUserId) {
        this.appointmentId = appointmentId;
        this.fromStatus = from;
        this.toStatus = to;
        this.action = action;
        this.details = details;
        this.changedByUserId = changedByUserId;
    }

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getAppointmentId() { return appointmentId; }
    public AppointmentStatus getFromStatus() { return fromStatus; }
    public AppointmentStatus getToStatus() { return toStatus; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Long getChangedByUserId() { return changedByUserId; }
    public Instant getChangedAt() { return changedAt; }
}
