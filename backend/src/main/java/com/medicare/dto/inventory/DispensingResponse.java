package com.medicare.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DispensingResponse(
        Long id,
        String dispensingNo,
        Long prescriptionId,
        String prescriptionNo,
        String patientName,
        String patientCode,
        String doctorName,
        String dispensedBy,
        Instant dispensedAt,
        String notes,
        List<Item> items) {

    public record Item(String prescribedMedicine, String medicineName, String strength, String unit, String batchNumber,
                       LocalDate expiryDate, int quantity, BigDecimal unitPrice) {
    }
}
