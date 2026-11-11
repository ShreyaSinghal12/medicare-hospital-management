package com.medicare.dto.inventory;

import java.time.Instant;

public record DispensingSummary(
        Long id,
        String dispensingNo,
        String prescriptionNo,
        String patientName,
        String patientCode,
        String dispensedBy,
        Instant dispensedAt,
        int itemCount,
        int totalQuantity) {
}
