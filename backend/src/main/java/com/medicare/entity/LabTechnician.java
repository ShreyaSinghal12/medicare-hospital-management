package com.medicare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "lab_technicians")
public class LabTechnician extends StaffProfile {

    @Column(length = 150)
    private String qualification;

    protected LabTechnician() {
        // for JPA
    }

    public LabTechnician(User user, String employeeCode, String qualification) {
        super(user, employeeCode);
        this.qualification = qualification;
    }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
}
