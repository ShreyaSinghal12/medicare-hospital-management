package com.medicare.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Front-desk staff profile (table {@code receptionists}): login user plus employee code. */
@Entity
@Table(name = "receptionists")
public class Receptionist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "employee_code", nullable = false, length = 20, unique = true)
    private String employeeCode;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Receptionist() {
        // for JPA
    }

    public Receptionist(User user, String employeeCode) {
        this.user = user;
        this.employeeCode = employeeCode;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEmployeeCode() { return employeeCode; }
    public Instant getCreatedAt() { return createdAt; }
}
