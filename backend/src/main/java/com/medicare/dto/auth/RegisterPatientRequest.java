package com.medicare.dto.auth;

import java.time.LocalDate;

import com.medicare.enums.BloodGroup;
import com.medicare.enums.Gender;
import com.medicare.validation.PasswordConfirmation;
import com.medicare.validation.PasswordsMatch;
import com.medicare.validation.StrongPassword;
import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Public patient self-registration (POST /api/auth/register). */
@PasswordsMatch
public record RegisterPatientRequest(
        @NotBlank @Size(min = 2, max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank @StrongPassword
        String password,

        @NotBlank
        String confirmPassword,

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
        String emergencyContactPhone) implements PasswordConfirmation {

    @Override
    public String newPasswordValue() {
        return password;
    }
}
