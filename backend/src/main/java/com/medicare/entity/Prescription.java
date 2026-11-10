package com.medicare.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.medicare.enums.PrescriptionStatus;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A digital prescription version. All versions of one prescription share {@code prescriptionNo};
 * {@code versionNumber} increases with each revision and {@code previousVersionId} links the chain.
 * Only DRAFT versions are mutable.
 */
@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_no", nullable = false, length = 30)
    private String prescriptionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionStatus status = PrescriptionStatus.DRAFT;

    @Column(name = "version_number", nullable = false)
    private int versionNumber = 1;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "amendment_reason", length = 500)
    private String amendmentReason;

    @Column(name = "general_instructions", length = 1000)
    private String generalInstructions;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<PrescriptionItem> items = new ArrayList<>();

    protected Prescription() {
        // for JPA
    }

    public Prescription(String prescriptionNo, Consultation consultation) {
        this.prescriptionNo = prescriptionNo;
        this.consultation = consultation;
        this.patient = consultation.getPatient();
        this.doctor = consultation.getDoctor();
    }

    /** A new DRAFT version of a finalized prescription, with copied items. */
    public Prescription newRevision(String reason) {
        Prescription next = new Prescription(prescriptionNo, consultation);
        next.versionNumber = versionNumber + 1;
        next.previousVersionId = id;
        next.amendmentReason = reason;
        next.generalInstructions = generalInstructions;
        items.forEach(i -> next.items.add(i.copyTo(next)));
        return next;
    }

    public void replaceItems(List<PrescriptionItem> newItems) {
        items.clear();
        items.addAll(newItems);
    }

    public void finalizeVersion() {
        status = PrescriptionStatus.FINALIZED;
        finalizedAt = Instant.now();
    }

    public void supersede() {
        status = PrescriptionStatus.SUPERSEDED;
    }

    public Long getId() { return id; }
    public String getPrescriptionNo() { return prescriptionNo; }
    public Consultation getConsultation() { return consultation; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public PrescriptionStatus getStatus() { return status; }
    public int getVersionNumber() { return versionNumber; }
    public Long getPreviousVersionId() { return previousVersionId; }
    public String getAmendmentReason() { return amendmentReason; }
    public String getGeneralInstructions() { return generalInstructions; }
    public void setGeneralInstructions(String generalInstructions) { this.generalInstructions = generalInstructions; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<PrescriptionItem> getItems() { return items; }
}
