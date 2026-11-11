package com.medicare.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A test in the laboratory catalog, maintained by administrators. */
@Entity
@Table(name = "lab_test_catalog")
public class LabTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "sample_type", length = 50)
    private String sampleType;

    @Column(length = 30)
    private String unit;

    @Column(name = "reference_range", length = 100)
    private String referenceRange;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "turnaround_hours", nullable = false)
    private int turnaroundHours;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected LabTest() {
        // for JPA
    }

    public LabTest(String code) {
        this.code = code;
    }

    public void update(String code, String name, String description, String sampleType, String unit,
                       String referenceRange, BigDecimal price, int turnaroundHours) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.sampleType = sampleType;
        this.unit = unit;
        this.referenceRange = referenceRange;
        this.price = price;
        this.turnaroundHours = turnaroundHours;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getSampleType() { return sampleType; }
    public String getUnit() { return unit; }
    public String getReferenceRange() { return referenceRange; }
    public BigDecimal getPrice() { return price; }
    public int getTurnaroundHours() { return turnaroundHours; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
