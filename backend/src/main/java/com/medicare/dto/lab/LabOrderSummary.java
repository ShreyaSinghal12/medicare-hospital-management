package com.medicare.dto.lab;

import java.time.Instant;
import java.util.List;

import com.medicare.enums.LabOrderStatus;
import com.medicare.enums.LabPriority;

public record LabOrderSummary(
        Long id,
        String orderNo,
        LabOrderStatus status,
        LabPriority priority,
        Long patientId,
        String patientName,
        String patientCode,
        Long doctorId,
        String doctorName,
        List<String> tests,
        boolean hasReport,
        Instant orderedAt,
        Instant completedAt) {
}
