package com.medicare.dto.lab;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.medicare.enums.LabOrderStatus;
import com.medicare.enums.LabPriority;

/**
 * A lab order as the viewer may see it. Patients never get {@code clinicalNotes}, and results/report only
 * once the order is COMPLETED. {@code canManage} and {@code allowedTransitions} drive the doctor's buttons.
 */
public record LabOrderResponse(
        Long id,
        String orderNo,
        LabOrderStatus status,
        LabPriority priority,
        String clinicalNotes,
        Long consultationId,
        Long appointmentId,
        LocalDate visitDate,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        String specialization,
        Instant orderedAt,
        Instant sampleCollectedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        List<LabOrderItemResponse> items,
        LabReportInfo report,
        long version,
        boolean canManage,
        List<LabOrderStatus> allowedTransitions) {
}
