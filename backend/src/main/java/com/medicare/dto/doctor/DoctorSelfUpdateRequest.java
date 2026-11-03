package com.medicare.dto.doctor;

import com.medicare.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What a doctor may edit on their own profile. Fee, department and licence are admin-managed. */
public record DoctorSelfUpdateRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE)
        String phone,

        @NotBlank @Size(max = 255)
        String qualifications,

        @Size(max = 2000)
        String bio) {
}
