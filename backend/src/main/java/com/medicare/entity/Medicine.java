package com.medicare.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.medicare.enums.MedicineCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A medicine in the hospital's inventory catalog (administrators maintain it). */
@Entity
@Table(name = "medicines")
public class Medicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "generic_name", length = 150)
    private String genericName;

    @Column(nullable = false, length = 150)
    private String manufacturer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MedicineCategory category;

    @Column(nullable = false, length = 50)
    private String strength;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "reorder_level", nullable = false)
    private int reorderLevel;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Medicine() {
        // for JPA
    }

    public Medicine(String name) {
        this.name = name;
    }

    public void update(String name, String genericName, String manufacturer, MedicineCategory category, String strength,
                       String unit, BigDecimal unitPrice, int reorderLevel) {
        this.name = name;
        this.genericName = genericName;
        this.manufacturer = manufacturer;
        this.category = category;
        this.strength = strength;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.reorderLevel = reorderLevel;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGenericName() { return genericName; }
    public String getManufacturer() { return manufacturer; }
    public MedicineCategory getCategory() { return category; }
    public String getStrength() { return strength; }
    public String getUnit() { return unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getReorderLevel() { return reorderLevel; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
