package com.medicare.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;

import com.medicare.enums.InvoiceStatus;

public record InvoiceSummary(
        Long id,
        String invoiceNo,
        InvoiceStatus status,
        Long patientId,
        String patientName,
        String patientCode,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        Instant issuedAt) {
}
