package com.medicare.dto.inventory;

import java.time.LocalDate;
import java.util.List;

public record InventoryAlerts(
        int expiryWarningDays,
        List<MedicineSummary> lowStock,
        List<AlertBatch> expiringSoon,
        List<AlertBatch> expired) {

    public record AlertBatch(Long batchId, Long medicineId, String medicineName, String strength, String unit,
                             String batchNumber, LocalDate expiryDate, int quantityAvailable, long daysLeft) {
    }
}
