package com.medicare.controller;

import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.appointment.AppointmentHistoryResponse;
import com.medicare.dto.appointment.AppointmentResponse;
import com.medicare.dto.appointment.BookAppointmentRequest;
import com.medicare.dto.appointment.CancelAppointmentRequest;
import com.medicare.dto.appointment.PatientAppointmentSummary;
import com.medicare.dto.appointment.RescheduleAppointmentRequest;
import com.medicare.dto.common.PageResponse;
import com.medicare.security.CurrentUser;
import com.medicare.service.AppointmentService;

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

/**
 * Appointments. Record-level rules (own appointment, cancellation window, valid status
 * transitions) are enforced in {@link AppointmentService}.
 */
@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PATIENT','RECEPTIONIST','ADMIN')")
    @Operation(summary = "Book a slot (409 SLOT_ALREADY_BOOKED if someone else got it first)")
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request) {
        return appointmentService.book(CurrentUser.get(), request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Own appointments: scope = UPCOMING | PAST | CANCELLED")
    public PageResponse<AppointmentResponse> mine(@RequestParam(defaultValue = "UPCOMING") String scope,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return appointmentService.listForPatient(CurrentUser.get(), scope, page, size);
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Counts and next appointment for the patient dashboard")
    public PatientAppointmentSummary summary() {
        return appointmentService.summaryForPatient(CurrentUser.get());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One appointment (patient: own; doctor: own; reception/admin: any)")
    public AppointmentResponse get(@PathVariable Long id) {
        return appointmentService.get(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Status and reschedule history of an appointment")
    public List<AppointmentHistoryResponse> history(@PathVariable Long id) {
        return appointmentService.history(CurrentUser.get(), id);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel; releases the slot")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelAppointmentRequest request) {
        return appointmentService.cancel(CurrentUser.get(), id, request);
    }

    @PatchMapping("/{id}/reschedule")
    @Operation(summary = "Move to another slot of the same doctor (atomic)")
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleAppointmentRequest request) {
        return appointmentService.reschedule(CurrentUser.get(), id, request);
    }

    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('DOCTOR','RECEPTIONIST','ADMIN')")
    @Operation(summary = "Record that the patient did not attend (after the start time)")
    public AppointmentResponse noShow(@PathVariable Long id) {
        return appointmentService.markNoShow(CurrentUser.get(), id);
    }
}
