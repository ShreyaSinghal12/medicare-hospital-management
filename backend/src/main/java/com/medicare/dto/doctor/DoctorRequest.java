package com.medicare.dto.doctor;

import java.math.BigDecimal;

import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update a doctor (admin). {@code email} is used only on creation;
 * it is ignored on update (e-mail identities are not changed in this prototype).
 */
public record DoctorRequest(
        @NotBlank @Size(min = 2, max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @Email @Size(max = 150)
        String email,

        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        @NotNull
        Long departmentId,

        @NotBlank @Size(max = 100)
        String specialization,

        @NotBlank @Size(max = 255)
        String qualifications,

        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9/\\-]+$", message = "Licence number may contain letters, digits, '/' and '-'")
        String licenseNumber,

        @NotNull @Min(0) @Max(70)
        Integer experienceYears,

        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2)
        BigDecimal consultationFee,

        @NotNull @Min(5) @Max(120)
        Integer slotDurationMinutes,

        @Size(max = 2000)
        String bio) {
}
