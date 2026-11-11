package com.medicare.dto.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BatchResponse(
        Long id,
        String batchNumber,
        LocalDate expiryDate,
        int quantityReceived,
        int quantityAvailable,
        BigDecimal purchasePrice,
        String supplier,
        Instant receivedAt,
        boolean expired,
        boolean expiringSoon) {
}
