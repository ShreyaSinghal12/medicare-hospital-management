package com.medicare.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.medicare.enums.PaymentMethod;
import com.medicare.enums.PaymentType;

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

/** A payment or refund. Never updated or deleted; a refund is its own row. No card data is stored. */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, length = 30, updatable = false)
    private String paymentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20, updatable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private PaymentMethod method;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "transaction_reference", length = 100, updatable = false)
    private String transactionReference;

    @Column(length = 500, updatable = false)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false, updatable = false)
    private User recordedBy;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt;

    protected Payment() {
        // for JPA
    }

    public Payment(String paymentNo, Invoice invoice, PaymentType type, PaymentMethod method, BigDecimal amount,
                   String transactionReference, String notes, User recordedBy) {
        this.paymentNo = paymentNo;
        this.invoice = invoice;
        this.type = type;
        this.method = method;
        this.amount = amount;
        this.transactionReference = transactionReference;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.paidAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPaymentNo() { return paymentNo; }
    public Invoice getInvoice() { return invoice; }
    public PaymentType getType() { return type; }
    public PaymentMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public String getTransactionReference() { return transactionReference; }
    public String getNotes() { return notes; }
    public User getRecordedBy() { return recordedBy; }
    public Instant getPaidAt() { return paidAt; }
}
