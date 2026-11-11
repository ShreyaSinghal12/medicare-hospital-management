package com.medicare.dto.billing;

import java.math.BigDecimal;

import com.medicare.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A payment (or, with {@code reason}, a refund). Never send card numbers: only a terminal or UPI reference. */
public record PaymentRequest(
        @NotNull PaymentMethod method,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 100) String transactionReference,
        @Size(max = 500) String notes) {
}
