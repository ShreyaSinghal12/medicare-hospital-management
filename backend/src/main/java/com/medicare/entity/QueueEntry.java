package com.medicare.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.medicare.enums.QueueStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A checked-in appointment's place in a doctor's queue for one day (token 1, 2, 3, ...). */
@Entity
@Table(name = "queue_entries")
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueueStatus status = QueueStatus.WAITING;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected QueueEntry() {
        // for JPA
    }

    public QueueEntry(Appointment appointment, int tokenNumber) {
        this.appointment = appointment;
        this.doctorId = appointment.getDoctor().getId();
        this.queueDate = appointment.getAppointmentDate();
        this.tokenNumber = tokenNumber;
        this.checkedInAt = Instant.now();
    }

    public void moveTo(QueueStatus target) {
        if (status == target) {
            return;
        }
        status = target;
        if (target == QueueStatus.IN_CONSULTATION && calledAt == null) {
            calledAt = Instant.now();
        }
        if (target == QueueStatus.COMPLETED) {
            completedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public Long getDoctorId() { return doctorId; }
    public LocalDate getQueueDate() { return queueDate; }
    public int getTokenNumber() { return tokenNumber; }
    public QueueStatus getStatus() { return status; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getCompletedAt() { return completedAt; }
}
