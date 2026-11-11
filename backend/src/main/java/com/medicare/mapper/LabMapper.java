package com.medicare.mapper;

import java.util.List;

import com.medicare.dto.lab.LabOrderItemResponse;
import com.medicare.dto.lab.LabOrderResponse;
import com.medicare.dto.lab.LabOrderSummary;
import com.medicare.dto.lab.LabReportInfo;
import com.medicare.dto.lab.LabTestResponse;
import com.medicare.entity.LabOrder;
import com.medicare.entity.LabOrderItem;
import com.medicare.entity.LabReport;
import com.medicare.entity.LabTest;
import com.medicare.enums.LabOrderStatus;

public final class LabMapper {

    /** Who is looking: the ordering doctor, another treating doctor, or the patient. */
    public enum Viewer { ORDERING_DOCTOR, TREATING_DOCTOR, PATIENT }

    private LabMapper() {
    }

    public static LabTestResponse toTest(LabTest t) {
        return new LabTestResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.getSampleType(),
                t.getUnit(), t.getReferenceRange(), t.getPrice(), t.getTurnaroundHours(), t.isActive());
    }

    public static LabOrderSummary toSummary(LabOrder o, Viewer viewer) {
        LabReport r = o.getReport();
        boolean hasReport = r != null && (viewer == Viewer.ORDERING_DOCTOR || r.isFinalized());
        return new LabOrderSummary(o.getId(), o.getOrderNo(), o.getStatus(), o.getPriority(), o.getPatient().getId(),
                o.getPatient().getUser().getFullName(), o.getPatient().getPatientCode(), o.getDoctor().getId(),
                o.getDoctor().getUser().getFullName(), o.getItems().stream().map(LabOrderItem::getTestName).toList(),
                hasReport, o.getOrderedAt(), o.getCompletedAt());
    }

    public static LabOrderResponse toResponse(LabOrder o, Viewer viewer) {
        boolean owner = viewer == Viewer.ORDERING_DOCTOR;
        boolean released = o.getStatus() == LabOrderStatus.COMPLETED;
        boolean showResults = owner || released;
        LabReport r = o.getReport();
        LabReportInfo report = r != null && (owner || r.isFinalized())
                ? new LabReportInfo(r.getOriginalFilename(), r.getFileSizeBytes(), r.getUploadedAt(),
                        r.getUploadedBy().getFullName(), r.isFinalized(), r.getFinalizedAt())
                : null;
        List<LabOrderItemResponse> items = o.getItems().stream()
                .map(i -> new LabOrderItemResponse(i.getId(), i.getTest().getId(), i.getTestName(), i.getPriceAtOrder(),
                        i.getUnit(), i.getReferenceRange(), showResults ? i.getResultValue() : null,
                        showResults ? i.getResultComment() : null))
                .toList();
        var c = o.getConsultation();
        var d = o.getDoctor();
        var p = o.getPatient();
        return new LabOrderResponse(o.getId(), o.getOrderNo(), o.getStatus(), o.getPriority(),
                viewer == Viewer.PATIENT ? null : o.getClinicalNotes(), c.getId(), c.getAppointment().getId(),
                c.getAppointment().getAppointmentDate(), p.getId(), p.getUser().getFullName(), p.getPatientCode(),
                d.getId(), d.getUser().getFullName(), d.getSpecialization(), o.getOrderedAt(), o.getSampleCollectedAt(),
                o.getCompletedAt(), o.getCancelledAt(), o.getCancellationReason(), items, report, o.getVersion(),
                owner && o.getStatus().isOpen(),
                owner ? List.copyOf(o.getStatus().manualTargets()) : List.of());
    }
}
