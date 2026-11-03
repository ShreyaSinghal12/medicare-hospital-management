package com.medicare.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.medicare.enums.ConsultationStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Clinical notes of one appointment. All clinical content is typed by the doctor; the system
 * never suggests diagnoses, treatments or doses. Once FINALIZED the note can only change
 * through {@link ConsultationAmendment}s, which preserve the previous values.
 */
@Entity
@Table(name = "consultations")
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(columnDefinition = "TEXT")
    private String symptoms;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "treatment_plan", columnDefinition = "TEXT")
    private String treatmentPlan;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "follow_up_notes", length = 500)
    private String followUpNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultationStatus status = ConsultationStatus.DRAFT;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "amendment_count", nullable = false)
    private int amendmentCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Consultation() {
        // for JPA
    }

    public Consultation(Appointment appointment) {
        this.appointment = appointment;
        this.patient = appointment.getPatient();
        this.doctor = appointment.getDoctor();
    }

    public void applyContent(String symptoms, String observations, String diagnosis, String treatmentPlan,
                             String notes, LocalDate followUpDate, String followUpNotes) {
        this.symptoms = symptoms;
        this.observations = observations;
        this.diagnosis = diagnosis;
        this.treatmentPlan = treatmentPlan;
        this.notes = notes;
        this.followUpDate = followUpDate;
        this.followUpNotes = followUpNotes;
    }

    public void finalizeNote() {
        this.status = ConsultationStatus.FINALIZED;
        this.finalizedAt = Instant.now();
    }

    public void recordAmendment() {
        this.amendmentCount++;
    }

    public Long getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public String getSymptoms() { return symptoms; }
    public String getObservations() { return observations; }
    public String getDiagnosis() { return diagnosis; }
    public String getTreatmentPlan() { return treatmentPlan; }
    public String getNotes() { return notes; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public String getFollowUpNotes() { return followUpNotes; }
    public ConsultationStatus getStatus() { return status; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public int getAmendmentCount() { return amendmentCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
