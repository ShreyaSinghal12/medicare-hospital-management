package com.medicare.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "receptionists")
public class Receptionist extends StaffProfile {

    protected Receptionist() {
        // for JPA
    }

    public Receptionist(User user, String employeeCode) {
        super(user, employeeCode);
    }
}
