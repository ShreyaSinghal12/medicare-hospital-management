package com.medicare.dto.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.medicare.enums.MedicineCategory;

/** Catalog entry with stock figures. Usable stock excludes expired batches. */
public record MedicineSummary(
        Long id,
        String name,
        String genericName,
        String manufacturer,
        MedicineCategory category,
        String strength,
        String unit,
        BigDecimal unitPrice,
        int reorderLevel,
        boolean active,
        int availableQuantity,
        int expiredQuantity,
        LocalDate nearestExpiry,
        boolean lowStock) {
}
