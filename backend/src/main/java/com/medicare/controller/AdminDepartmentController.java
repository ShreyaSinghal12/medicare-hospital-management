package com.medicare.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.department.DepartmentRequest;
import com.medicare.dto.department.DepartmentResponse;
import com.medicare.dto.department.DepartmentStatusRequest;
import com.medicare.service.DepartmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/departments")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: departments")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    public AdminDepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @Operation(summary = "All departments, including inactive ones")
    public List<DepartmentResponse> list() {
        return departmentService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest request) {
        return departmentService.create(request);
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest request) {
        return departmentService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate (blocked while active doctors are assigned)")
    public DepartmentResponse setStatus(@PathVariable Long id, @Valid @RequestBody DepartmentStatusRequest request) {
        return departmentService.setActive(id, request.active());
    }
}
