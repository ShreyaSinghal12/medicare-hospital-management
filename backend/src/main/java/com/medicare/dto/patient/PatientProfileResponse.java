package com.medicare.dto.patient;

import java.time.Instant;
import java.time.LocalDate;

import com.medicare.enums.BloodGroup;
import com.medicare.enums.Gender;

/** Patient demographic profile. Contains no clinical data. */
public record PatientProfileResponse(
        Long id,
        String patientCode,
        Long userId,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        Integer age,
        Gender gender,
        BloodGroup bloodGroup,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        boolean active,
        Instant registeredAt) {
}
