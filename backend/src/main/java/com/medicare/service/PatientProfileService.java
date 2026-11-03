package com.medicare.service;

import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.dto.patient.UpdatePatientProfileRequest;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.exception.ResourceNotFoundException;
import com.medicare.mapper.PatientMapper;
import com.medicare.repository.PatientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A patient's own demographic profile. Only non-clinical fields are editable here;
 * diagnoses, allergies and prescriptions are entered exclusively by doctors.
 */
@Service
public class PatientProfileService {

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    public PatientProfileService(PatientRepository patientRepository, AuditService auditService) {
        this.patientRepository = patientRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getOwnProfile(Long userId) {
        return PatientMapper.toProfile(findByUser(userId));
    }

    @Transactional
    public PatientProfileResponse updateOwnProfile(Long userId, UpdatePatientProfileRequest req) {
        Patient patient = findByUser(userId);
        User user = patient.getUser();
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone().trim());
        patient.setDateOfBirth(req.dateOfBirth());
        patient.setGender(req.gender());
        patient.setBloodGroup(req.bloodGroup());
        patient.setAddress(blankToNull(req.address()));
        patient.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        patient.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        patientRepository.flush();
        auditService.record(AuditService.Actions.PATIENT_PROFILE_UPDATED, "Patient", patient.getId(),
                AuditOutcome.SUCCESS, "Self-service demographic update");
        return PatientMapper.toProfile(patient);
    }

    private Patient findByUser(Long userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient profile not found for the current user"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
