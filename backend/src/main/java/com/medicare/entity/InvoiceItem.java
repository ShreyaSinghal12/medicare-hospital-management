package com.medicare.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.medicare.enums.InvoiceItemType;

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

/**
 * One invoice line. For charges taken from appointments, lab tests or dispensings, {@code billedReference}
 * (e.g. {@code LAB_TEST:17}) is unique across all active invoices, so each charge is billed once.
 */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20, updatable = false)
    private InvoiceItemType type;

    @Column(name = "reference_id", updatable = false)
    private Long referenceId;

    @Column(nullable = false, length = 255, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    @Column(name = "billed_reference", length = 40)
    private String billedReference;

    protected InvoiceItem() {
        // for JPA
    }

    public InvoiceItem(Invoice invoice, InvoiceItemType type, Long referenceId, String description, int quantity, BigDecimal unitPrice) {
        this.invoice = invoice;
        this.type = type;
        this.referenceId = referenceId;
        this.description = description.length() > 255 ? description.substring(0, 255) : description;
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);
        this.lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        this.billedReference = referenceId == null ? null : key(type, referenceId);
    }

    public static String key(InvoiceItemType type, Long referenceId) {
        return type.name() + ":" + referenceId;
    }

    /** Frees the charge for another invoice (used when the invoice is cancelled). */
    void release() {
        billedReference = null;
    }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public InvoiceItemType getType() { return type; }
    public Long getReferenceId() { return referenceId; }
    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public String getBilledReference() { return billedReference; }
}
