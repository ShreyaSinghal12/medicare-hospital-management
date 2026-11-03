package com.medicare.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Snapshot (JSON) of a finalized consultation's values before an amendment, with who and why. */
@Entity
@Table(name = "consultation_amendments")
public class ConsultationAmendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consultation_id", nullable = false)
    private Long consultationId;

    @Column(name = "amended_by_doctor_id", nullable = false)
    private Long amendedByDoctorId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "previous_snapshot", nullable = false, columnDefinition = "JSON")
    private String previousSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConsultationAmendment() {
        // for JPA
    }

    public ConsultationAmendment(Long consultationId, Long amendedByDoctorId, String reason, String previousSnapshot) {
        this.consultationId = consultationId;
        this.amendedByDoctorId = amendedByDoctorId;
        this.reason = reason;
        this.previousSnapshot = previousSnapshot;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getConsultationId() { return consultationId; }
    public Long getAmendedByDoctorId() { return amendedByDoctorId; }
    public String getReason() { return reason; }
    public String getPreviousSnapshot() { return previousSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
}
