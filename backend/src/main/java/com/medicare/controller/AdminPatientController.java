package com.medicare.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.patient.AdminUpdatePatientRequest;
import com.medicare.dto.patient.DuplicatePatientGroup;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.service.PatientAdminService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Patient administration: demographics only. No clinical data is reachable from here. */
@RestController
@RequestMapping("/api/admin/patients")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: patients")
public class AdminPatientController {

    private final PatientAdminService patientAdminService;

    public AdminPatientController(PatientAdminService patientAdminService) {
        this.patientAdminService = patientAdminService;
    }

    @GetMapping
    @Operation(summary = "Search patients by name, e-mail, phone or patient ID")
    public PageResponse<PatientProfileResponse> search(@RequestParam(required = false) String search,
                                                       @RequestParam(required = false) Boolean active,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return patientAdminService.search(search, active, page, size);
    }

    @GetMapping("/duplicates")
    @Operation(summary = "Possible duplicate registrations (same name + DOB, or same phone)")
    public List<DuplicatePatientGroup> duplicates() {
        return patientAdminService.findDuplicates();
    }

    @GetMapping("/{id}")
    public PatientProfileResponse get(@PathVariable Long id) {
        return patientAdminService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Non-clinical correction of a patient record (reason is audited)")
    public PatientProfileResponse correct(@PathVariable Long id, @Valid @RequestBody AdminUpdatePatientRequest request) {
        return patientAdminService.correct(id, request);
    }
}
