package com.medicare.enums;

/**
 * The six user roles. Spring Security authorities are "ROLE_" + name(),
 * so {@code @PreAuthorize("hasRole('ADMIN')")} matches {@link #ADMIN}.
 */
public enum Role {
    PATIENT,
    DOCTOR,
    RECEPTIONIST,
    ADMIN,
    LAB_TECHNICIAN,
    PHARMACIST;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Staff roles are created by an administrator, never by self-registration. */
    public boolean isStaff() {
        return this != PATIENT;
    }
}
