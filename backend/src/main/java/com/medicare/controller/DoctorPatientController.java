package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.clinical.ActiveFlagRequest;
import com.medicare.dto.clinical.AllergyRequest;
import com.medicare.dto.clinical.AllergyResponse;
import com.medicare.dto.clinical.ConditionRequest;
import com.medicare.dto.clinical.ConditionResponse;
import com.medicare.dto.clinical.ConditionStatusRequest;
import com.medicare.dto.clinical.PatientRecordResponse;
import com.medicare.dto.common.PageResponse;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.security.CurrentUser;
import com.medicare.service.PatientRecordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Patient records for treating doctors (treatment relationship required; access is audited). */
@RestController
@RequestMapping("/api/doctor/patients")
@PreAuthorize("hasRole('DOCTOR')")
@Tag(name = "Doctor: patient records")
public class DoctorPatientController {

    private final PatientRecordService recordService;

    public DoctorPatientController(PatientRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    @Operation(summary = "Patients you have (had) appointments with")
    public PageResponse<PatientProfileResponse> myPatients(@RequestParam(required = false) String search,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return recordService.myPatients(CurrentUser.get(), search, page, size);
    }

    @GetMapping("/{patientId}/record")
    @Operation(summary = "Clinical record (403 without a treatment relationship)")
    public PatientRecordResponse record(@PathVariable Long patientId) {
        return recordService.record(CurrentUser.get(), patientId);
    }

    @PostMapping("/{patientId}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    public AllergyResponse addAllergy(@PathVariable Long patientId, @Valid @RequestBody AllergyRequest request) {
        return recordService.addAllergy(CurrentUser.get(), patientId, request);
    }

    @PatchMapping("/{patientId}/allergies/{allergyId}")
    public AllergyResponse setAllergyActive(@PathVariable Long patientId, @PathVariable Long allergyId,
                                            @Valid @RequestBody ActiveFlagRequest request) {
        return recordService.setAllergyActive(CurrentUser.get(), patientId, allergyId, request.active());
    }

    @PostMapping("/{patientId}/conditions")
    @ResponseStatus(HttpStatus.CREATED)
    public ConditionResponse addCondition(@PathVariable Long patientId, @Valid @RequestBody ConditionRequest request) {
        return recordService.addCondition(CurrentUser.get(), patientId, request);
    }

    @PatchMapping("/{patientId}/conditions/{conditionId}")
    public ConditionResponse setConditionStatus(@PathVariable Long patientId, @PathVariable Long conditionId,
                                                @Valid @RequestBody ConditionStatusRequest request) {
        return recordService.setConditionStatus(CurrentUser.get(), patientId, conditionId, request.status());
    }
}
