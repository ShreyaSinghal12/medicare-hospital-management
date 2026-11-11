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

/** Quantity of one prescription line taken from one batch. */
@Entity
@Table(name = "dispensing_items")
public class DispensingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispensing_id", nullable = false, updatable = false)
    private DispensingRecord record;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_item_id", nullable = false, updatable = false)
    private PrescriptionItem prescriptionItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false, updatable = false)
    private Medicine medicine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private MedicineBatch batch;

    @Column(nullable = false, updatable = false)
    private int quantity;

    protected DispensingItem() {
        // for JPA
    }

    DispensingItem(DispensingRecord record, PrescriptionItem prescriptionItem, MedicineBatch batch, int quantity) {
        this.record = record;
        this.prescriptionItem = prescriptionItem;
        this.medicine = batch.getMedicine();
        this.batch = batch;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public DispensingRecord getRecord() { return record; }
    public PrescriptionItem getPrescriptionItem() { return prescriptionItem; }
    public Medicine getMedicine() { return medicine; }
    public MedicineBatch getBatch() { return batch; }
    public int getQuantity() { return quantity; }
}
