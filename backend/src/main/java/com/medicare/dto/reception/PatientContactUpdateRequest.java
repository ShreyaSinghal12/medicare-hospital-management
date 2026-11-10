package com.medicare.dto.reception;

import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The contact details reception may update. Name, date of birth and all clinical data are excluded. */
public record PatientContactUpdateRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone,
        @Size(max = 500) String address,
        @Size(max = 120) String emergencyContactName,
        @Pattern(regexp = "^$|" + ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String emergencyContactPhone) {
}
