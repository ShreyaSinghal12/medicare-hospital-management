package com.medicare.entity;

import java.time.Instant;

import com.medicare.enums.AllergySeverity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Doctor-recorded allergy. Deactivated (not deleted) when no longer relevant. */
@Entity
@Table(name = "patient_allergies")
public class PatientAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 150)
    private String allergen;

    @Column(length = 255)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AllergySeverity severity;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "recorded_by_doctor_id")
    private Long recordedByDoctorId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PatientAllergy() {
        // for JPA
    }

    public PatientAllergy(Long patientId, String allergen, String reaction, AllergySeverity severity, Long doctorId) {
        this.patientId = patientId;
        this.allergen = allergen;
        this.reaction = reaction;
        this.severity = severity;
        this.recordedByDoctorId = doctorId;
    }

    public Long getId() { return id; }
    public Long getPatientId() { return patientId; }
    public String getAllergen() { return allergen; }
    public String getReaction() { return reaction; }
    public AllergySeverity getSeverity() { return severity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getRecordedByDoctorId() { return recordedByDoctorId; }
    public Instant getCreatedAt() { return createdAt; }
}
