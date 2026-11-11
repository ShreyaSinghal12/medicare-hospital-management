package com.medicare.dto.lab;

import java.math.BigDecimal;

public record LabOrderItemResponse(
        Long id,
        Long testId,
        String testName,
        BigDecimal price,
        String unit,
        String referenceRange,
        String resultValue,
        String resultComment) {
}
