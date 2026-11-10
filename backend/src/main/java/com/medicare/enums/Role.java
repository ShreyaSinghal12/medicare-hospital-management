package com.medicare.enums;

/**
 * The four user roles: patients, doctors, front-desk receptionists and administrators.
 * Laboratory work is handled by doctors (orders, report uploads) and administrators (test catalog);
 * medicine inventory and dispensing records are handled by administrators.
 *
 * <p>Spring Security authorities are "ROLE_" + name(), so {@code @PreAuthorize("hasRole('ADMIN')")}
 * matches {@link #ADMIN}.</p>
 */
public enum Role {
    PATIENT,
    DOCTOR,
    RECEPTIONIST,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Staff roles are created by an administrator, never by self-registration. */
    public boolean isStaff() {
        return this != PATIENT;
    }
}
