package com.medicare.dto.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medicare.enums.InvoiceItemType;
import com.medicare.enums.InvoiceStatus;
import com.medicare.enums.PaymentMethod;
import com.medicare.enums.PaymentType;

public record InvoiceResponse(
        Long id,
        String invoiceNo,
        InvoiceStatus status,
        Long patientId,
        String patientName,
        String patientCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxRatePercent,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal refundedAmount,
        BigDecimal balanceDue,
        String notes,
        String issuedBy,
        Instant issuedAt,
        Instant cancelledAt,
        String cancellationReason,
        List<Item> items,
        List<PaymentLine> payments,
        boolean canPay,
        boolean canRefund,
        boolean canCancel) {

    public record Item(Long id, InvoiceItemType type, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record PaymentLine(Long id, String paymentNo, PaymentType type, PaymentMethod method, BigDecimal amount,
                              String transactionReference, String notes, String recordedBy, Instant paidAt) {
    }
}
