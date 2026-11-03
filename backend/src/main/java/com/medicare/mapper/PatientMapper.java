package com.medicare.mapper;

import java.time.LocalDate;
import java.time.Period;

import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.entity.Patient;
import com.medicare.entity.User;

public final class PatientMapper {

    private PatientMapper() {
    }

    public static PatientProfileResponse toProfile(Patient p) {
        User u = p.getUser();
        Integer age = p.getDateOfBirth() == null ? null : Period.between(p.getDateOfBirth(), LocalDate.now()).getYears();
        return new PatientProfileResponse(p.getId(), p.getPatientCode(), u.getId(), u.getFullName(), u.getEmail(),
                u.getPhone(), p.getDateOfBirth(), age, p.getGender(), p.getBloodGroup(), p.getAddress(),
                p.getEmergencyContactName(), p.getEmergencyContactPhone(), u.isActive(), p.getCreatedAt());
    }
}
