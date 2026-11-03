package com.medicare.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.medicare.enums.ConditionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Doctor-recorded health condition (e.g. a fictional "hypertension"). */
@Entity
@Table(name = "patient_conditions")
public class PatientCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "condition_name", nullable = false, length = 150)
    private String conditionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConditionStatus status = ConditionStatus.ACTIVE;

    @Column(name = "diagnosed_on")
    private LocalDate diagnosedOn;

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by_doctor_id")
    private Long recordedByDoctorId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected PatientCondition() {
        // for JPA
    }

    public PatientCondition(Long patientId, String conditionName, ConditionStatus status, LocalDate diagnosedOn,
                            String notes, Long doctorId) {
        this.patientId = patientId;
        this.conditionName = conditionName;
        this.status = status;
        this.diagnosedOn = diagnosedOn;
        this.notes = notes;
        this.recordedByDoctorId = doctorId;
    }

    public Long getId() { return id; }
    public Long getPatientId() { return patientId; }
    public String getConditionName() { return conditionName; }
    public ConditionStatus getStatus() { return status; }
    public void setStatus(ConditionStatus status) { this.status = status; }
    public LocalDate getDiagnosedOn() { return diagnosedOn; }
    public String getNotes() { return notes; }
    public Long getRecordedByDoctorId() { return recordedByDoctorId; }
    public Instant getCreatedAt() { return createdAt; }
}
