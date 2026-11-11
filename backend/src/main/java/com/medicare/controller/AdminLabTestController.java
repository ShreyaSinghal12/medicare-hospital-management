package com.medicare.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.lab.LabTestRequest;
import com.medicare.dto.lab.LabTestResponse;
import com.medicare.dto.lab.LabTestStatusRequest;
import com.medicare.service.LabCatalogService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Laboratory test catalog maintenance (administrators only; no access to orders or results). */
@RestController
@RequestMapping("/api/admin/lab-tests")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: laboratory catalog")
public class AdminLabTestController {

    private final LabCatalogService catalogService;

    public AdminLabTestController(LabCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(summary = "All tests, including inactive ones")
    public List<LabTestResponse> list(@RequestParam(required = false) String search) {
        return catalogService.list(search, false);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabTestResponse create(@Valid @RequestBody LabTestRequest request) {
        return catalogService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a test (existing orders keep the name, price and range they were ordered with)")
    public LabTestResponse update(@PathVariable Long id, @Valid @RequestBody LabTestRequest request) {
        return catalogService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activate or deactivate (inactive tests cannot be ordered)")
    public LabTestResponse setStatus(@PathVariable Long id, @Valid @RequestBody LabTestStatusRequest request) {
        return catalogService.setActive(id, request.active());
    }
}
