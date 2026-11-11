package com.medicare.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.lab.CreateLabOrderRequest;
import com.medicare.dto.lab.LabOrderResponse;
import com.medicare.dto.lab.LabOrderStatusRequest;
import com.medicare.dto.lab.LabOrderSummary;
import com.medicare.dto.lab.LabResultsRequest;
import com.medicare.dto.lab.LabTestResponse;
import com.medicare.enums.LabOrderStatus;
import com.medicare.exception.BusinessRuleException;
import com.medicare.security.CurrentUser;
import com.medicare.service.LabCatalogService;
import com.medicare.service.LabOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Laboratory: doctors order tests, record results and upload/finalize reports; patients read and download
 * their finalized reports. Reading endpoints accept any logged-in user so that denied attempts by other
 * roles reach the service and are audited.
 */
@RestController
@Tag(name = "Laboratory")
public class LabController {

    private final LabOrderService orderService;
    private final LabCatalogService catalogService;

    public LabController(LabOrderService orderService, LabCatalogService catalogService) {
        this.orderService = orderService;
        this.catalogService = catalogService;
    }

    @GetMapping("/api/lab-tests")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    @Operation(summary = "Active catalog tests (for ordering)")
    public List<LabTestResponse> catalog(@RequestParam(required = false) String search) {
        return catalogService.list(search, true);
    }

    @PostMapping("/api/lab-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Order tests for one of your consultations")
    public LabOrderResponse create(@Valid @RequestBody CreateLabOrderRequest request) {
        return orderService.create(CurrentUser.get(), request);
    }

    @GetMapping("/api/lab-orders/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Order details (patients see results only after finalization)")
    public LabOrderResponse get(@PathVariable Long id) {
        return orderService.get(CurrentUser.get(), id);
    }

    @PatchMapping("/api/lab-orders/{id}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Sample collected, processing, or cancelled (reason required)")
    public LabOrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody LabOrderStatusRequest request) {
        return orderService.changeStatus(CurrentUser.get(), id, request);
    }

    @PutMapping("/api/lab-orders/{id}/results")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Save result values typed by the ordering doctor (send version; 409 if stale)")
    public LabOrderResponse saveResults(@PathVariable Long id, @Valid @RequestBody LabResultsRequest request) {
        return orderService.saveResults(CurrentUser.get(), id, request);
    }

    @PostMapping(path = "/api/lab-orders/{id}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Upload or replace the PDF report (max 5 MB, plain PDF only)")
    public LabOrderResponse upload(@PathVariable Long id, @RequestParam(value = "file", required = false) MultipartFile file,
                                   @RequestParam(required = false) Long version) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("FILE_REQUIRED", "Choose a PDF file to upload");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
        return orderService.uploadReport(CurrentUser.get(), id, version, file.getOriginalFilename(), bytes);
    }

    @DeleteMapping("/api/lab-orders/{id}/report")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Remove an unfinalized report")
    public LabOrderResponse removeReport(@PathVariable Long id, @RequestParam(required = false) Long version) {
        return orderService.removeReport(CurrentUser.get(), id, version);
    }

    @PostMapping("/api/lab-orders/{id}/finalize")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Finalize: locks results and report, releases them to the patient and notifies them")
    public LabOrderResponse finalizeReport(@PathVariable Long id, @RequestParam(required = false) Long version) {
        return orderService.finalizeReport(CurrentUser.get(), id, version);
    }

    @GetMapping("/api/lab-orders/{id}/report")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download the report PDF (patients: finalized only)")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        LabOrderService.Download d = orderService.download(CurrentUser.get(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(d.content());
    }

    @GetMapping("/api/consultations/{consultationId}/lab-orders")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<LabOrderSummary> forConsultation(@PathVariable Long consultationId) {
        return orderService.forConsultation(CurrentUser.get(), consultationId);
    }

    @GetMapping("/api/doctor/lab-orders")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Orders you placed (status filter, search by order no., patient or test)")
    public PageResponse<LabOrderSummary> doctorOrders(@RequestParam(required = false) LabOrderStatus status,
                                                      @RequestParam(required = false) String search,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        return orderService.listForDoctor(CurrentUser.get(), status, search, page, size);
    }

    @GetMapping("/api/doctor/patients/{patientId}/lab-orders")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Lab orders of a patient you are treating (audited)")
    public PageResponse<LabOrderSummary> patientOrders(@PathVariable Long patientId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        return orderService.forPatientOfDoctor(CurrentUser.get(), patientId, page, size);
    }

    @GetMapping("/api/patients/me/lab-orders")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Own lab orders; results are visible once a report is finalized")
    public PageResponse<LabOrderSummary> mine(@RequestParam(required = false) LabOrderStatus status,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return orderService.listForPatient(CurrentUser.get(), status, search, page, size);
    }
}
