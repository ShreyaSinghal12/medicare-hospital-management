package com.medicare.dto.lab;

import java.math.BigDecimal;

public record LabTestResponse(
        Long id,
        String code,
        String name,
        String description,
        String sampleType,
        String unit,
        String referenceRange,
        BigDecimal price,
        int turnaroundHours,
        boolean active) {
}
