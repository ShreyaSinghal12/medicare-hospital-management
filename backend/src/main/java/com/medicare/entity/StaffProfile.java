package com.medicare.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToOne;

/**
 * Common mapping for non-clinical staff profiles (receptionists, lab technicians,
 * pharmacists): a link to the login {@link User} plus an employee code.
 * Demonstrates inheritance via {@code @MappedSuperclass} (each subclass keeps its own table).
 */
@MappedSuperclass
public abstract class StaffProfile {

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

    protected StaffProfile() {
        // for JPA
    }

    protected StaffProfile(User user, String employeeCode) {
        this.user = user;
        this.employeeCode = employeeCode;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEmployeeCode() { return employeeCode; }
    public Instant getCreatedAt() { return createdAt; }
}
