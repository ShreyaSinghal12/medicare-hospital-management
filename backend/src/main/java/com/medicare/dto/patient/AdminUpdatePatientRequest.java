package com.medicare.dto.patient;

import java.time.LocalDate;

import com.medicare.enums.Gender;
import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Non-clinical corrections an administrator may make to a patient record
 * (registration troubleshooting). Blood group and all clinical data are excluded.
 */
public record AdminUpdatePatientRequest(
        @NotBlank @Size(min = 2, max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        @NotNull @Past
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 500)
        String address,

        @Size(max = 120)
        String emergencyContactName,

        @Pattern(regexp = "^$|" + ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String emergencyContactPhone,

        @NotBlank @Size(min = 5, max = 300)
        String reason) {
}
