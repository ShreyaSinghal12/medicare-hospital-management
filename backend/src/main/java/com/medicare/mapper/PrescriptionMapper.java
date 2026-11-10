package com.medicare.mapper;

import com.medicare.dto.prescription.PrescriptionItemDto;
import com.medicare.dto.prescription.PrescriptionResponse;
import com.medicare.dto.prescription.PrescriptionSummary;
import com.medicare.entity.Prescription;
import com.medicare.entity.PrescriptionItem;

public final class PrescriptionMapper {

    private PrescriptionMapper() {
    }

    public static PrescriptionItemDto toItem(PrescriptionItem i) {
        return new PrescriptionItemDto(i.getMedicineName(), i.getStrength(), i.getDosage(), i.getFrequency(),
                i.getDuration(), i.getInstructions(), i.getQuantity());
    }

    public static PrescriptionResponse toResponse(Prescription p, boolean canEdit, boolean canRevise) {
        var c = p.getConsultation();
        var d = p.getDoctor();
        var pt = p.getPatient();
        return new PrescriptionResponse(p.getId(), p.getPrescriptionNo(), p.getVersionNumber(), p.getStatus(),
                p.getPreviousVersionId(), p.getAmendmentReason(), c.getId(), c.getAppointment().getId(),
                c.getAppointment().getAppointmentDate(), pt.getId(), pt.getUser().getFullName(), pt.getPatientCode(),
                d.getId(), d.getUser().getFullName(), d.getSpecialization(), d.getDepartment().getName(),
                p.getGeneralInstructions(), p.getItems().stream().map(PrescriptionMapper::toItem).toList(),
                p.getFinalizedAt(), p.getCreatedAt(), p.getVersion(), canEdit, canRevise);
    }

    public static PrescriptionSummary toSummary(Prescription p) {
        return new PrescriptionSummary(p.getId(), p.getPrescriptionNo(), p.getVersionNumber(), p.getStatus(),
                p.getConsultation().getAppointment().getAppointmentDate(), p.getPatient().getUser().getFullName(),
                p.getPatient().getPatientCode(), p.getDoctor().getId(), p.getDoctor().getUser().getFullName(),
                p.getDoctor().getSpecialization(),
                p.getItems().stream().map(PrescriptionItem::getMedicineName).toList(),
                p.getFinalizedAt(), p.getCreatedAt());
    }
}
