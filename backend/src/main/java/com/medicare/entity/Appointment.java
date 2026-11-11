package com.medicare.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AppointmentType;
import com.medicare.exception.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** An appointment. Dates and times are local to the hospital's time zone. */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_no", nullable = false, length = 30, unique = true)
    private String referenceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private AppointmentSlot slot;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_type", nullable = false, length = 20)
    private AppointmentType appointmentType = AppointmentType.NEW;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;

    @Column(name = "needs_reschedule", nullable = false)
    private boolean needsReschedule;

    @Column(name = "follow_up_of_id")
    private Long followUpOfId;

    @Column(name = "booked_by_user_id", nullable = false)
    private Long bookedByUserId;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** The doctor's fee when the appointment was booked (null for bookings made before this was recorded). */
    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private java.math.BigDecimal consultationFee;

    protected Appointment() {
        // for JPA
    }

    public Appointment(String referenceNo, Patient patient, Doctor doctor, AppointmentSlot slot, LocalDate date,
                       LocalTime start, LocalTime end, AppointmentType type, String reason, Long bookedByUserId) {
        this.referenceNo = referenceNo;
        this.patient = patient;
        this.doctor = doctor;
        this.slot = slot;
        this.appointmentDate = date;
        this.startTime = start;
        this.endTime = end;
        this.appointmentType = type;
        this.reason = reason;
        this.bookedByUserId = bookedByUserId;
        this.consultationFee = doctor.getConsultationFee();
    }

    /** Applies a status change, enforcing the allowed transitions of {@link AppointmentStatus}. */
    public AppointmentStatus transitionTo(AppointmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "An appointment that is " + status + " cannot be changed to " + target);
        }
        AppointmentStatus previous = status;
        status = target;
        Instant now = Instant.now();
        switch (target) {
            case CHECKED_IN -> checkedInAt = now;
            case IN_PROGRESS -> startedAt = now;
            case COMPLETED -> completedAt = now;
            case CANCELLED -> cancelledAt = now;
            default -> { }
        }
        return previous;
    }

    public void cancel(Long byUserId, String reason) {
        transitionTo(AppointmentStatus.CANCELLED);
        this.cancelledByUserId = byUserId;
        this.cancellationReason = reason;
        this.needsReschedule = false;
    }

    public void moveTo(AppointmentSlot newSlot, LocalDate date, LocalTime start, LocalTime end) {
        if (status != AppointmentStatus.SCHEDULED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only scheduled appointments can be rescheduled (current status: " + status + ")");
        }
        this.slot = newSlot;
        this.appointmentDate = date;
        this.startTime = start;
        this.endTime = end;
        this.rescheduleCount++;
        this.needsReschedule = false;
    }

    public Long getId() { return id; }
    public java.math.BigDecimal getConsultationFee() { return consultationFee; }
    public String getReferenceNo() { return referenceNo; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public AppointmentSlot getSlot() { return slot; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public AppointmentType getAppointmentType() { return appointmentType; }
    public String getReason() { return reason; }
    public AppointmentStatus getStatus() { return status; }
    public boolean isNeedsReschedule() { return needsReschedule; }
    public void setNeedsReschedule(boolean needsReschedule) { this.needsReschedule = needsReschedule; }
    public Long getFollowUpOfId() { return followUpOfId; }
    public void setFollowUpOfId(Long followUpOfId) { this.followUpOfId = followUpOfId; }
    public Long getBookedByUserId() { return bookedByUserId; }
    public Long getCancelledByUserId() { return cancelledByUserId; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getRescheduleCount() { return rescheduleCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
