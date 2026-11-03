package com.medicare.entity;

import java.time.LocalDate;
import java.time.LocalTime;

import com.medicare.enums.SlotStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Reservation ledger row. The unique key (doctor_id, slot_date, start_time) guarantees at the
 * database level that a doctor's slot can be held by only one booking at a time.
 */
@Entity
@Table(name = "appointment_slots")
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SlotStatus status;

    @Version
    private long version;

    protected AppointmentSlot() {
        // for JPA
    }

    public AppointmentSlot(Long doctorId, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        this.doctorId = doctorId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SlotStatus.BOOKED;
    }

    public void book(LocalTime end) {
        this.endTime = end;
        this.status = SlotStatus.BOOKED;
    }

    public void release() {
        this.status = SlotStatus.AVAILABLE;
    }

    public Long getId() { return id; }
    public Long getDoctorId() { return doctorId; }
    public LocalDate getSlotDate() { return slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public SlotStatus getStatus() { return status; }
}
