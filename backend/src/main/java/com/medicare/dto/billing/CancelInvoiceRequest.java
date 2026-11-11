package com.medicare.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInvoiceRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
}
