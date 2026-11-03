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

    /** One finalized consultation. Prescriptions and lab results are attached in later phases. */
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
            boolean amended) {
    }
}
