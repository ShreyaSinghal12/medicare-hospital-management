package com.medicare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pharmacists")
public class Pharmacist extends StaffProfile {

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    protected Pharmacist() {
        // for JPA
    }

    public Pharmacist(User user, String employeeCode, String licenseNumber) {
        super(user, employeeCode);
        this.licenseNumber = licenseNumber;
    }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
}
