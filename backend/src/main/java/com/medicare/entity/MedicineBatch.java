package com.medicare.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.medicare.exception.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A received batch of one medicine. {@code quantityAvailable} only changes together with an
 * {@link InventoryTransaction}; the database also refuses negative stock.
 */
@Entity
@Table(name = "medicine_batches")
public class MedicineBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Column(name = "batch_number", nullable = false, length = 50)
    private String batchNumber;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "quantity_received", nullable = false)
    private int quantityReceived;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "purchase_price", precision = 10, scale = 2)
    private BigDecimal purchasePrice;

    @Column(length = 150)
    private String supplier;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Version
    private long version;

    protected MedicineBatch() {
        // for JPA
    }

    public MedicineBatch(Medicine medicine, String batchNumber, LocalDate expiryDate, int quantity,
                         BigDecimal purchasePrice, String supplier) {
        this.medicine = medicine;
        this.batchNumber = batchNumber;
        this.expiryDate = expiryDate;
        this.quantityReceived = quantity;
        this.quantityAvailable = quantity;
        this.purchasePrice = purchasePrice;
        this.supplier = supplier;
        this.receivedAt = Instant.now();
    }

    /** Applies a signed change and returns the new balance; never goes below zero. */
    public int apply(int signedQuantity) {
        int next = quantityAvailable + signedQuantity;
        if (next < 0) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    "Batch " + batchNumber + " has only " + quantityAvailable + " " + medicine.getUnit() + " left");
        }
        quantityAvailable = next;
        return next;
    }

    public boolean isExpired(LocalDate today) {
        return expiryDate.isBefore(today);
    }

    public Long getId() { return id; }
    public Medicine getMedicine() { return medicine; }
    public String getBatchNumber() { return batchNumber; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public int getQuantityReceived() { return quantityReceived; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public String getSupplier() { return supplier; }
    public Instant getReceivedAt() { return receivedAt; }
    public long getVersion() { return version; }
}
