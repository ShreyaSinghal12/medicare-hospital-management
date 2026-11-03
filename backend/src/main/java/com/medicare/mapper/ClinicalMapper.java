package com.medicare.mapper;

import com.medicare.dto.clinical.AllergyResponse;
import com.medicare.dto.clinical.ConditionResponse;
import com.medicare.dto.clinical.ConsultationResponse;
import com.medicare.dto.clinical.ConsultationSummary;
import com.medicare.dto.clinical.MedicalHistoryResponse;
import com.medicare.dto.clinical.VisitSummary;
import com.medicare.entity.Appointment;
import com.medicare.entity.Consultation;
import com.medicare.entity.PatientAllergy;
import com.medicare.entity.PatientCondition;

public final class ClinicalMapper {

    private ClinicalMapper() {
    }

    /**
     * @param clinicianView true for doctors (all fields); false for the patient
     *                      (observations and internal notes omitted)
     */
    public static ConsultationResponse toResponse(Consultation c, boolean clinicianView, boolean canEdit, boolean canAmend) {
        Appointment a = c.getAppointment();
        var d = c.getDoctor();
        var p = c.getPatient();
        return new ConsultationResponse(c.getId(), a.getId(), a.getReferenceNo(), a.getStatus(), a.getAppointmentDate(),
                a.getStartTime(), clinicianView ? a.getReason() : null, p.getId(), p.getUser().getFullName(),
                p.getPatientCode(), d.getId(), d.getUser().getFullName(), d.getSpecialization(), d.getDepartment().getName(),
                c.getSymptoms(), clinicianView ? c.getObservations() : null, c.getDiagnosis(), c.getTreatmentPlan(),
                clinicianView ? c.getNotes() : null, c.getFollowUpDate(), c.getFollowUpNotes(), c.getStatus(),
                c.getFinalizedAt(), c.getAmendmentCount(), c.getVersion(), c.getUpdatedAt(), canEdit, canAmend);
    }

    public static ConsultationSummary toSummary(Consultation c, Long viewerDoctorId) {
        var a = c.getAppointment();
        var d = c.getDoctor();
        return new ConsultationSummary(c.getId(), a.getId(), a.getAppointmentDate(), d.getUser().getFullName(),
                d.getSpecialization(), c.getDiagnosis(), c.getStatus(), c.getAmendmentCount(),
                d.getId().equals(viewerDoctorId));
    }

    public static MedicalHistoryResponse.TimelineEntry toTimeline(Consultation c) {
        var a = c.getAppointment();
        var d = c.getDoctor();
        return new MedicalHistoryResponse.TimelineEntry(c.getId(), a.getId(), a.getAppointmentDate(), a.getStartTime(),
                d.getId(), d.getUser().getFullName(), d.getSpecialization(), d.getDepartment().getName(),
                c.getSymptoms(), c.getDiagnosis(), c.getTreatmentPlan(), c.getFollowUpDate(), c.getFollowUpNotes(),
                c.getAmendmentCount() > 0);
    }

    public static VisitSummary toVisit(Appointment a) {
        return new VisitSummary(a.getId(), a.getReferenceNo(), a.getAppointmentDate(), a.getStartTime(),
                a.getDoctor().getUser().getFullName(), a.getDoctor().getDepartment().getName(), a.getStatus());
    }

    public static AllergyResponse toAllergy(PatientAllergy a, String recordedBy) {
        return new AllergyResponse(a.getId(), a.getAllergen(), a.getReaction(), a.getSeverity(), a.isActive(),
                recordedBy, a.getCreatedAt());
    }

    public static ConditionResponse toCondition(PatientCondition c, String recordedBy) {
        return new ConditionResponse(c.getId(), c.getConditionName(), c.getStatus(), c.getDiagnosedOn(), c.getNotes(),
                recordedBy, c.getCreatedAt());
    }
}
