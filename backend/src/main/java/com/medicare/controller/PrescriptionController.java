package com.medicare.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.prescription.PrescriptionRequest;
import com.medicare.dto.prescription.PrescriptionResponse;
import com.medicare.dto.prescription.PrescriptionSummary;
import com.medicare.dto.prescription.RevisePrescriptionRequest;
import com.medicare.enums.PrescriptionStatus;
import com.medicare.security.CurrentUser;
import com.medicare.service.PrescriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Prescriptions: doctors write, patients read and download their own. */
@RestController
@Tag(name = "Prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @PostMapping("/api/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Create a draft prescription for one of your consultations")
    public PrescriptionResponse create(@Valid @RequestBody PrescriptionRequest request) {
        return prescriptionService.create(CurrentUser.get(), request);
    }

    @PutMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Replace the medicines/instructions of a draft (send version; 409 if stale)")
    public PrescriptionResponse update(@PathVariable Long id, @Valid @RequestBody PrescriptionRequest request) {
        return prescriptionService.updateDraft(CurrentUser.get(), id, request);
    }

    @DeleteMapping("/api/prescriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Discard a draft")
    public void discard(@PathVariable Long id) {
        prescriptionService.discardDraft(CurrentUser.get(), id);
    }

    @PostMapping("/api/prescriptions/{id}/finalize")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Finalize; supersedes the previous version and notifies the patient")
    public PrescriptionResponse finalizePrescription(@PathVariable Long id, @RequestParam(required = false) Long version) {
        return prescriptionService.finalizePrescription(CurrentUser.get(), id, version);
    }

    @PostMapping("/api/prescriptions/{id}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Start a new draft version of a finalized prescription")
    public PrescriptionResponse revise(@PathVariable Long id, @Valid @RequestBody RevisePrescriptionRequest request) {
        return prescriptionService.revise(CurrentUser.get(), id, request.reason());
    }

    @GetMapping("/api/prescriptions/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
    public PrescriptionResponse get(@PathVariable Long id) {
        return prescriptionService.get(CurrentUser.get(), id);
    }

    @GetMapping("/api/prescriptions/{id}/versions")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
    @Operation(summary = "All visible versions of this prescription number")
    public List<PrescriptionSummary> versions(@PathVariable Long id) {
        return prescriptionService.versions(CurrentUser.get(), id);
    }

    @GetMapping("/api/prescriptions/{id}/pdf")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
    @Operation(summary = "Download the PDF of a finalized (or superseded) version")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        PrescriptionService.Pdf pdf = prescriptionService.pdf(CurrentUser.get(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf.content());
    }

    @GetMapping("/api/consultations/{consultationId}/prescriptions")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<PrescriptionResponse> forConsultation(@PathVariable Long consultationId) {
        return prescriptionService.forConsultation(CurrentUser.get(), consultationId);
    }

    @GetMapping("/api/patients/me/prescriptions")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Own prescriptions: search (number, doctor, medicine), doctor and date filters")
    public PageResponse<PrescriptionSummary> mine(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) Long doctorId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(defaultValue = "false") boolean includeSuperseded,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return prescriptionService.listForPatient(CurrentUser.get(), search, doctorId, from, to, includeSuperseded, page, size);
    }

    @GetMapping("/api/doctor/prescriptions")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Prescriptions you issued")
    public PageResponse<PrescriptionSummary> issued(@RequestParam(required = false) String search,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                    @RequestParam(required = false) PrescriptionStatus status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return prescriptionService.listForDoctor(CurrentUser.get(), search, from, to, status, page, size);
    }
}
