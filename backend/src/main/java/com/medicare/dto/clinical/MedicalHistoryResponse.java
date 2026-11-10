package com.medicare.dto.clinical;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/** The patient's own medical history: active allergies, conditions and a visit timeline. */
public record MedicalHistoryResponse(
        List<AllergyResponse> allergies,
        List<ConditionResponse> conditions,
        List<TimelineEntry> timeline) {

    /** One finalized consultation with its current prescriptions (lab results are attached in Phase 8). */
    public record TimelineEntry(
            Long consultationId,
            Long appointmentId,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            Long doctorId,
            String doctorName,
            String specialization,
            String departmentName,
            String symptoms,
            String diagnosis,
            String treatmentPlan,
            LocalDate followUpDate,
            String followUpNotes,
            boolean amended,
            List<PrescriptionRef> prescriptions) {
    }

    /** Current (finalized) prescription issued in that consultation. */
    public record PrescriptionRef(Long id, String prescriptionNo, int versionNumber, List<String> medicines) {
    }
}
