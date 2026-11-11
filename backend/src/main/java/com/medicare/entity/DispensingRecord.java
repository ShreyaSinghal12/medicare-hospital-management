package com.medicare.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

/** Medicines handed out against one finalized prescription version. Immutable once saved. */
@Entity
@Table(name = "dispensing_records")
public class DispensingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispensing_no", nullable = false, length = 30, updatable = false)
    private String dispensingNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispensed_by_user_id", nullable = false, updatable = false)
    private User dispensedBy;

    @Column(length = 500, updatable = false)
    private String notes;

    @Column(name = "dispensed_at", nullable = false, updatable = false)
    private Instant dispensedAt;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<DispensingItem> items = new ArrayList<>();

    protected DispensingRecord() {
        // for JPA
    }

    public DispensingRecord(String dispensingNo, Prescription prescription, User dispensedBy, String notes) {
        this.dispensingNo = dispensingNo;
        this.prescription = prescription;
        this.patient = prescription.getPatient();
        this.dispensedBy = dispensedBy;
        this.notes = notes;
        this.dispensedAt = Instant.now();
    }

    public void addItem(PrescriptionItem prescriptionItem, MedicineBatch batch, int quantity) {
        items.add(new DispensingItem(this, prescriptionItem, batch, quantity));
    }

    public Long getId() { return id; }
    public String getDispensingNo() { return dispensingNo; }
    public Prescription getPrescription() { return prescription; }
    public Patient getPatient() { return patient; }
    public User getDispensedBy() { return dispensedBy; }
    public String getNotes() { return notes; }
    public Instant getDispensedAt() { return dispensedAt; }
    public List<DispensingItem> getItems() { return items; }
}
