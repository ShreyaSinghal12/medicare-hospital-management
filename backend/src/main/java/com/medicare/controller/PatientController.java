package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.clinical.MedicalHistoryResponse;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.dto.patient.UpdatePatientProfileRequest;
import com.medicare.security.CurrentUser;
import com.medicare.service.PatientProfileService;
import com.medicare.service.PatientRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the logged-in patient's own data. The patient id is never taken from the URL. */
@RestController
@RequestMapping("/api/patients/me")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient self-service")
public class PatientController {

    private final PatientProfileService profileService;
    private final PatientRecordService recordService;

    public PatientController(PatientProfileService profileService, PatientRecordService recordService) {
        this.profileService = profileService;
        this.recordService = recordService;
    }

    @GetMapping("/medical-history")
    @Operation(summary = "Own allergies, conditions and finalized consultations (timeline)")
    public MedicalHistoryResponse medicalHistory() {
        return recordService.myHistory(CurrentUser.get());
    }

    @GetMapping
    @Operation(summary = "Get own patient profile")
    public PatientProfileResponse getProfile() {
        return profileService.getOwnProfile(CurrentUser.id());
    }

    @PutMapping
    @Operation(summary = "Update own demographic profile (no clinical fields)")
    public PatientProfileResponse updateProfile(@Valid @RequestBody UpdatePatientProfileRequest request) {
        return profileService.updateOwnProfile(CurrentUser.id(), request);
    }
}
