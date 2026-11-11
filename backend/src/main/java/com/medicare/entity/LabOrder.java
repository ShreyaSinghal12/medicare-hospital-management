package com.medicare.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.medicare.enums.LabOrderStatus;
import com.medicare.enums.LabPriority;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;

/** A doctor's laboratory order, always linked to one of that doctor's consultations. */
@Entity
@Table(name = "lab_orders")
public class LabOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

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
    private LabOrderStatus status = LabOrderStatus.ORDERED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LabPriority priority = LabPriority.ROUTINE;

    @Column(name = "clinical_notes", length = 1000)
    private String clinicalNotes;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "sample_collected_at")
    private Instant sampleCollectedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<LabOrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private LabReport report;

    protected LabOrder() {
        // for JPA
    }

    public LabOrder(String orderNo, Consultation consultation, LabPriority priority, String clinicalNotes) {
        this.orderNo = orderNo;
        this.consultation = consultation;
        this.patient = consultation.getPatient();
        this.doctor = consultation.getDoctor();
        this.priority = priority;
        this.clinicalNotes = clinicalNotes;
        this.orderedAt = Instant.now();
    }

    public void addItem(LabTest test) {
        items.add(new LabOrderItem(this, test));
    }

    public void changeStatus(LabOrderStatus target, String reason) {
        Instant now = Instant.now();
        switch (target) {
            case SAMPLE_COLLECTED -> sampleCollectedAt = now;
            case COMPLETED -> completedAt = now;
            case CANCELLED -> {
                cancelledAt = now;
                cancellationReason = reason;
            }
            default -> { }
        }
        status = target;
    }

    public void attachReport(LabReport report) {
        this.report = report;
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public Consultation getConsultation() { return consultation; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public LabOrderStatus getStatus() { return status; }
    public LabPriority getPriority() { return priority; }
    public String getClinicalNotes() { return clinicalNotes; }
    public Instant getOrderedAt() { return orderedAt; }
    public Instant getSampleCollectedAt() { return sampleCollectedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public List<LabOrderItem> getItems() { return items; }
    public LabReport getReport() { return report; }
}
