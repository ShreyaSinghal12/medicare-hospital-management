package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.staff.CreateStaffRequest;
import com.medicare.dto.staff.StaffResponse;
import com.medicare.enums.Role;
import com.medicare.service.StaffService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: staff", description = "Receptionists, lab technicians, pharmacists and administrators")
public class AdminStaffController {

    private final StaffService staffService;

    public AdminStaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public PageResponse<StaffResponse> search(@RequestParam(required = false) Role role,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return staffService.search(role, search, active, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a staff account; a set-password link is e-mailed to the new user")
    public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return staffService.create(request);
    }
}
