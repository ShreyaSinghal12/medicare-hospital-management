package com.medicare.dto.inventory;

import java.util.List;

public record MedicineDetail(MedicineSummary medicine, List<BatchResponse> batches, int expiryWarningDays) {
}
