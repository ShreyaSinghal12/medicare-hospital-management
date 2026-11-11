package com.medicare.dto.billing;

import java.math.BigDecimal;
import java.util.List;

public record BillableCharges(
        Long patientId,
        String patientName,
        String patientCode,
        BigDecimal taxRatePercent,
        BigDecimal outstandingBalance,
        List<BillableCharge> charges) {
}
