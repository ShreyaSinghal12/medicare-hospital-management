package com.medicare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One medicine line, written exactly as the doctor entered it. */
@Entity
@Table(name = "prescription_items")
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    /** Optional link to the inventory catalog (set from Phase 9 when dispensing is matched). */
    @Column(name = "medicine_id")
    private Long medicineId;

    @Column(name = "medicine_name", nullable = false, length = 150)
    private String medicineName;

    @Column(length = 50)
    private String strength;

    @Column(nullable = false, length = 100)
    private String dosage;

    @Column(nullable = false, length = 100)
    private String frequency;

    @Column(nullable = false, length = 100)
    private String duration;

    @Column(length = 500)
    private String instructions;

    @Column
    private Integer quantity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PrescriptionItem() {
        // for JPA
    }

    public PrescriptionItem(Prescription prescription, String medicineName, String strength, String dosage,
                            String frequency, String duration, String instructions, Integer quantity, int sortOrder) {
        this.prescription = prescription;
        this.medicineName = medicineName;
        this.strength = strength;
        this.dosage = dosage;
        this.frequency = frequency;
        this.duration = duration;
        this.instructions = instructions;
        this.quantity = quantity;
        this.sortOrder = sortOrder;
    }

    public PrescriptionItem copyTo(Prescription target) {
        PrescriptionItem copy = new PrescriptionItem(target, medicineName, strength, dosage, frequency, duration,
                instructions, quantity, sortOrder);
        copy.medicineId = medicineId;
        return copy;
    }

    public Long getId() { return id; }
    public Prescription getPrescription() { return prescription; }
    public Long getMedicineId() { return medicineId; }
    public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }
    public String getMedicineName() { return medicineName; }
    public String getStrength() { return strength; }
    public String getDosage() { return dosage; }
    public String getFrequency() { return frequency; }
    public String getDuration() { return duration; }
    public String getInstructions() { return instructions; }
    public Integer getQuantity() { return quantity; }
    public int getSortOrder() { return sortOrder; }
}
