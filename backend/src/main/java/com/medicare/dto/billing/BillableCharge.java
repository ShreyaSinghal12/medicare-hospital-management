package com.medicare.dto.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.medicare.enums.InvoiceItemType;

/** A service the patient has received and not yet been billed for. Prices come from the server, never the client. */
public record BillableCharge(
        InvoiceItemType type,
        Long referenceId,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        LocalDate serviceDate,
        String source) {
}
