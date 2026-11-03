package com.medicare.dto.clinical;

import java.util.List;

import com.medicare.dto.patient.PatientProfileResponse;

/** What a treating doctor sees when opening a patient's record. */
public record PatientRecordResponse(
        PatientProfileResponse patient,
        List<AllergyResponse> allergies,
        List<ConditionResponse> conditions,
        List<ConsultationSummary> consultations,
        List<VisitSummary> visits) {
}
