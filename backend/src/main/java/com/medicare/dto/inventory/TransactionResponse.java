package com.medicare.dto.inventory;

import java.time.Instant;

import com.medicare.enums.InventoryTxnType;

public record TransactionResponse(
        Long id,
        Long medicineId,
        String medicineName,
        String strength,
        Long batchId,
        String batchNumber,
        InventoryTxnType type,
        int quantity,
        int balanceAfter,
        Long dispensingId,
        String reference,
        String notes,
        String performedBy,
        Instant createdAt) {
}
