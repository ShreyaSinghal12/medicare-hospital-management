package com.medicare.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Metadata of the uploaded report PDF. The file itself is on disk under {@code storedFilename}. */
@Entity
@Table(name = "lab_reports")
public class LabReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_order_id", nullable = false)
    private LabOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 100)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String sha256;

    @Column(nullable = false)
    private boolean finalized;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    protected LabReport() {
        // for JPA
    }

    public LabReport(LabOrder order, User uploadedBy) {
        this.order = order;
        this.uploadedBy = uploadedBy;
    }

    /** Points this record at a newly stored file (only while not finalized). */
    public void replaceFile(User uploader, String originalFilename, String storedFilename, long size, String sha256) {
        this.uploadedBy = uploader;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = "application/pdf";
        this.fileSizeBytes = size;
        this.sha256 = sha256;
        this.uploadedAt = Instant.now();
    }

    public void finalizeReport() {
        this.finalized = true;
        this.finalizedAt = Instant.now();
    }

    public Long getId() { return id; }
    public LabOrder getOrder() { return order; }
    public User getUploadedBy() { return uploadedBy; }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoredFilename() { return storedFilename; }
    public String getContentType() { return contentType; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public String getSha256() { return sha256; }
    public boolean isFinalized() { return finalized; }
    public Instant getUploadedAt() { return uploadedAt; }
    public Instant getFinalizedAt() { return finalizedAt; }
}
