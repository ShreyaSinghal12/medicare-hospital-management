package com.medicare.dto.billing;

import java.math.BigDecimal;
import java.util.List;

import com.medicare.enums.InvoiceItemType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Charges are referenced by type and id only; the server looks up their prices. Other items (e.g. a dressing
 * or registration fee) are typed by the receptionist.
 */
public record CreateInvoiceRequest(
        @NotNull Long patientId,
        @Size(max = 100) List<@Valid ChargeRef> charges,
        @Size(max = 20) List<@Valid OtherItem> otherItems,
        @DecimalMin("0.00") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) BigDecimal discountAmount,
        @Size(max = 500) String notes) {

    public record ChargeRef(@NotNull InvoiceItemType type, @NotNull Long referenceId) {
    }

    public record OtherItem(
            @NotBlank @Size(min = 3, max = 200) String description,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            @NotNull @DecimalMin("0.01") @DecimalMax("100000.00") @Digits(integer = 8, fraction = 2) BigDecimal unitPrice) {
    }
}
