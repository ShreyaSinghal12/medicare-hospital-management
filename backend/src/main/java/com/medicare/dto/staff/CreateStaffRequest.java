package com.medicare.dto.staff;

import com.medicare.enums.Role;
import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creates a receptionist or another administrator. Doctors have their own endpoint
 * because they need professional details.
 */
public record CreateStaffRequest(
        @NotNull Role role,

        @NotBlank @Size(min = 2, max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = ValidationPatterns.PERSON_NAME_MESSAGE)
        String fullName,

        @NotBlank @Email @Size(max = 150)
        String email,

        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone) {
}
