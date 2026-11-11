package com.medicare.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.medicare.enums.InvoiceItemType;
import com.medicare.enums.InvoiceStatus;
import com.medicare.enums.PaymentMethod;

/**
 * Billing figures for a date range (hospital time zone, both dates inclusive). Outstanding amounts cover all
 * open invoices regardless of date.
 */
public record BillingSummary(
        LocalDate from,
        LocalDate to,
        long invoiceCount,
        BigDecimal invoicedTotal,
        BigDecimal collected,
        BigDecimal refunded,
        BigDecimal netCollected,
        long outstandingCount,
        BigDecimal outstandingTotal,
        Map<InvoiceStatus, Long> invoicesByStatus,
        Map<PaymentMethod, BigDecimal> collectedByMethod,
        Map<InvoiceItemType, BigDecimal> invoicedByType) {
}
