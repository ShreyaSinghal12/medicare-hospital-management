package com.medicare.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.medicare.enums.InvoiceStatus;

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

import org.hibernate.annotations.BatchSize;

/**
 * A patient invoice. Amounts are fixed when it is issued; afterwards only the money received, the money refunded
 * and the status change. {@code amountPaid} (all payments) never exceeds the total and {@code refundedAmount}
 * never exceeds {@code amountPaid} (also database checks). A refund is a credit for something not provided or
 * returned: it does not make the patient owe the amount again.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, length = 30, updatable = false)
    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRatePercent = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by_user_id", nullable = false, updatable = false)
    private User issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {
        // for JPA
    }

    public Invoice(String invoiceNo, Patient patient, User issuedBy, String notes) {
        this.invoiceNo = invoiceNo;
        this.patient = patient;
        this.issuedBy = issuedBy;
        this.notes = notes;
        this.issuedAt = Instant.now();
    }

    public void addItem(InvoiceItem item) {
        items.add(item);
    }

    /** Computes subtotal, tax and total from the items. Tax applies to the amount after discount. */
    public void computeTotals(BigDecimal discount, BigDecimal taxRate) {
        subtotal = items.stream().map(InvoiceItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        discountAmount = discount.setScale(2, RoundingMode.HALF_UP);
        taxRatePercent = taxRate.setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxable = subtotal.subtract(discountAmount);
        taxAmount = taxable.multiply(taxRatePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        totalAmount = taxable.add(taxAmount);
    }

    public BigDecimal balanceDue() {
        return totalAmount.subtract(amountPaid);
    }

    /** What can still be refunded (received minus already refunded). */
    public BigDecimal refundable() {
        return amountPaid.subtract(refundedAmount);
    }

    public void applyPayment(BigDecimal amount) {
        amountPaid = amountPaid.add(amount);
        status = amountPaid.compareTo(totalAmount) >= 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }

    /** Records a refund; the invoice is REFUNDED once everything received has been given back. */
    public void applyRefund(BigDecimal amount) {
        refundedAmount = refundedAmount.add(amount);
        if (refundedAmount.compareTo(amountPaid) == 0) {
            status = InvoiceStatus.REFUNDED;
        }
    }

    /** A zero-total invoice (e.g. fully discounted) is settled at once. */
    public void settleIfZero() {
        if (totalAmount.signum() == 0) {
            status = InvoiceStatus.PAID;
        }
    }

    public void cancel(String reason) {
        status = InvoiceStatus.CANCELLED;
        cancelledAt = Instant.now();
        cancellationReason = reason;
        items.forEach(InvoiceItem::release);
    }

    public Long getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public Patient getPatient() { return patient; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTaxRatePercent() { return taxRatePercent; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public InvoiceStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public User getIssuedBy() { return issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public long getVersion() { return version; }
    public List<InvoiceItem> getItems() { return items; }
}
