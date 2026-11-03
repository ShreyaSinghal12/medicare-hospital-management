package com.medicare.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Recurring weekly working hours of a doctor for one ISO day of week (1 = Monday … 7 = Sunday). */
@Entity
@Table(name = "doctor_availability")
public class DoctorAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "day_of_week", nullable = false, columnDefinition = "TINYINT")
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected DoctorAvailability() {
        // for JPA
    }

    public DoctorAvailability(Long doctorId, int dayOfWeek) {
        this.doctorId = doctorId;
        this.dayOfWeek = dayOfWeek;
    }

    public void update(LocalTime start, LocalTime end, LocalTime breakStart, LocalTime breakEnd, boolean active) {
        this.startTime = start;
        this.endTime = end;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
        this.active = active;
    }

    public Long getId() { return id; }
    public Long getDoctorId() { return doctorId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public LocalTime getBreakStart() { return breakStart; }
    public LocalTime getBreakEnd() { return breakEnd; }
    public boolean isActive() { return active; }
}
