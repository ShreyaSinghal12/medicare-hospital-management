package com.medicare.enums;

/**
 * DRAFT: editable by the prescribing doctor, invisible to the patient.
 * FINALIZED: the current, immutable prescription.
 * SUPERSEDED: an older finalized version replaced by a newer finalized revision (kept for history).
 */
public enum PrescriptionStatus {
    DRAFT,
    FINALIZED,
    SUPERSEDED
}
