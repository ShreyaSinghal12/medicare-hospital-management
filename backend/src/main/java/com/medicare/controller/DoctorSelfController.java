package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.doctor.AdminDoctorResponse;
import com.medicare.dto.doctor.DoctorSelfUpdateRequest;
import com.medicare.security.CurrentUser;
import com.medicare.service.DoctorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in doctor's own professional profile. */
@RestController
@RequestMapping("/api/doctor/profile")
@PreAuthorize("hasRole('DOCTOR')")
@Tag(name = "Doctor self-service")
public class DoctorSelfController {

    private final DoctorService doctorService;

    public DoctorSelfController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public AdminDoctorResponse get() {
        return doctorService.getOwn(CurrentUser.id());
    }

    @PutMapping
    @Operation(summary = "Update own phone, qualifications and bio (fee and department are admin-managed)")
    public AdminDoctorResponse update(@Valid @RequestBody DoctorSelfUpdateRequest request) {
        return doctorService.updateOwn(CurrentUser.id(), request);
    }
}
