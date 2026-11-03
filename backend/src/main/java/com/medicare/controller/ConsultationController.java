package com.medicare.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.clinical.AmendConsultationRequest;
import com.medicare.dto.clinical.AmendmentResponse;
import com.medicare.dto.clinical.ConsultationContent;
import com.medicare.dto.clinical.ConsultationResponse;
import com.medicare.dto.clinical.StartConsultationRequest;
import com.medicare.security.CurrentUser;
import com.medicare.service.ConsultationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consultations. Doctors write; patients read their own finalized notes. Receptionists and
 * administrators are refused at the URL level and again in the service.
 */
@RestController
@RequestMapping("/api/consultations")
@PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
@Tag(name = "Consultations")
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Start (or re-open) the consultation for one of your appointments today")
    public ConsultationResponse start(@Valid @RequestBody StartConsultationRequest request) {
        return consultationService.start(CurrentUser.get(), request.appointmentId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Treating doctors: full note; patient: own finalized note")
    public ConsultationResponse get(@PathVariable Long id) {
        return consultationService.get(CurrentUser.get(), id);
    }

    @GetMapping("/by-appointment/{appointmentId}")
    public ConsultationResponse byAppointment(@PathVariable Long appointmentId) {
        return consultationService.getByAppointment(CurrentUser.get(), appointmentId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Save draft (send the version you loaded; 409 if it changed meanwhile)")
    public ConsultationResponse saveDraft(@PathVariable Long id, @Valid @RequestBody ConsultationContent content) {
        return consultationService.saveDraft(CurrentUser.get(), id, content);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Save and finalize; completes the appointment")
    public ConsultationResponse finalizeConsultation(@PathVariable Long id, @Valid @RequestBody ConsultationContent content) {
        return consultationService.finalizeConsultation(CurrentUser.get(), id, content);
    }

    @PostMapping("/{id}/amendments")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Amend a finalized note; previous values are kept with the reason")
    public ConsultationResponse amend(@PathVariable Long id, @Valid @RequestBody AmendConsultationRequest request) {
        return consultationService.amend(CurrentUser.get(), id, request);
    }

    @GetMapping("/{id}/amendments")
    @PreAuthorize("hasRole('DOCTOR')")
    public List<AmendmentResponse> amendments(@PathVariable Long id) {
        return consultationService.amendments(CurrentUser.get(), id);
    }
}
