package com.medicare.dto.patient;

import java.time.LocalDate;

import com.medicare.enums.BloodGroup;
import com.medicare.enums.Gender;
import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fields a patient may update on their own profile (PUT /api/patients/me). */
public record UpdatePatientProfileRequest(
        @NotBlank @Size(min = 2, max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        @NotNull @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        Gender gender,

        BloodGroup bloodGroup,

        @Size(max = 500)
        String address,

        @Size(max = 120)
        String emergencyContactName,

        @Pattern(regexp = "^$|" + ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String emergencyContactPhone) {
}
