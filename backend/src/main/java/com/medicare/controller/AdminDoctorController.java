package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.doctor.AdminDoctorResponse;
import com.medicare.dto.doctor.DoctorRequest;
import com.medicare.service.DoctorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin doctor management. Doctors are never deleted (appointments and prescriptions
 * reference them); use PATCH /api/admin/users/{userId}/status to deactivate.
 */
@RestController
@RequestMapping("/api/admin/doctors")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: doctors")
public class AdminDoctorController {

    private final DoctorService doctorService;

    public AdminDoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    @Operation(summary = "Search doctors (all statuses)")
    public PageResponse<AdminDoctorResponse> search(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) Long departmentId,
                                                    @RequestParam(required = false) Boolean active,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return doctorService.searchAdmin(search, departmentId, active, page, size);
    }

    @GetMapping("/{id}")
    public AdminDoctorResponse get(@PathVariable Long id) {
        return doctorService.getAdmin(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a doctor account; a set-password link is e-mailed to the doctor")
    public AdminDoctorResponse create(@Valid @RequestBody DoctorRequest request) {
        return doctorService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update doctor profile, department and consultation fee")
    public AdminDoctorResponse update(@PathVariable Long id, @Valid @RequestBody DoctorRequest request) {
        return doctorService.update(id, request);
    }
}
