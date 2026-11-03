package com.medicare.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.medicare.enums.LeaveStatus;
import com.medicare.enums.LeaveType;

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

/** Leave (full days) or special unavailability (hours within the dates). Takes effect once APPROVED. */
@Entity
@Table(name = "doctor_leave")
public class DoctorLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected DoctorLeave() {
        // for JPA
    }

    public DoctorLeave(Doctor doctor, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                       LocalTime startTime, LocalTime endTime, String reason) {
        this.doctor = doctor;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }

    /** True when this (approved) leave blocks the interval [from, to) on {@code date}. */
    public boolean blocks(LocalDate date, LocalTime from, LocalTime to) {
        if (status != LeaveStatus.APPROVED || date.isBefore(startDate) || date.isAfter(endDate)) {
            return false;
        }
        if (leaveType == LeaveType.LEAVE || startTime == null || endTime == null) {
            return true;
        }
        return from.isBefore(endTime) && to.isAfter(startTime);
    }

    public void review(LeaveStatus decision, Long reviewerId) {
        this.status = decision;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Doctor getDoctor() { return doctor; }
    public LeaveType getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getReason() { return reason; }
    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
