package com.medicare.entity;

import java.time.Instant;

import com.medicare.enums.InventoryTxnType;

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

/** Append-only stock ledger row. Never updated or deleted. */
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false, updatable = false)
    private Medicine medicine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private MedicineBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20, updatable = false)
    private InventoryTxnType type;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    @Column(name = "dispensing_id", updatable = false)
    private Long dispensingId;

    @Column(length = 100, updatable = false)
    private String reference;

    @Column(length = 500, updatable = false)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by_user_id", nullable = false, updatable = false)
    private User performedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected InventoryTransaction() {
        // for JPA
    }

    public InventoryTransaction(MedicineBatch batch, InventoryTxnType type, int quantity, int balanceAfter,
                                Long dispensingId, String reference, String notes, User performedBy) {
        this.medicine = batch.getMedicine();
        this.batch = batch;
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.dispensingId = dispensingId;
        this.reference = reference;
        this.notes = notes;
        this.performedBy = performedBy;
    }

    public Long getId() { return id; }
    public Medicine getMedicine() { return medicine; }
    public MedicineBatch getBatch() { return batch; }
    public InventoryTxnType getType() { return type; }
    public int getQuantity() { return quantity; }
    public int getBalanceAfter() { return balanceAfter; }
    public Long getDispensingId() { return dispensingId; }
    public String getReference() { return reference; }
    public String getNotes() { return notes; }
    public User getPerformedBy() { return performedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
