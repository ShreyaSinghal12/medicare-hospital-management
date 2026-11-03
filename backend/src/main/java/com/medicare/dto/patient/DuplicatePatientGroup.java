package com.medicare.dto.patient;

import java.util.List;

/** Patients that may be the same person (same name + date of birth, or same phone number). */
public record DuplicatePatientGroup(String matchReason, String matchValue, List<PatientProfileResponse> patients) {
}
