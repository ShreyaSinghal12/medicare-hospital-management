package com.medicare.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.appointment.AppointmentResponse;
import com.medicare.dto.common.PageResponse;
import com.medicare.dto.patient.PatientProfileResponse;
import com.medicare.dto.reception.PatientContactUpdateRequest;
import com.medicare.dto.reception.QueueEntryResponse;
import com.medicare.dto.reception.ReceptionDashboard;
import com.medicare.dto.reception.WalkInRegistrationRequest;
import com.medicare.enums.AppointmentStatus;
import com.medicare.security.CurrentUser;
import com.medicare.service.AppointmentService;
import com.medicare.service.QueueService;
import com.medicare.service.ReceptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
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

/** Front-desk endpoints. Demographic and scheduling data only; no clinical content. */
@RestController
@RequestMapping("/api/reception")
@PreAuthorize("hasAnyRole('RECEPTIONIST','ADMIN')")
@Tag(name = "Reception", description = "Walk-ins, patient directory, daily calendar and queue")
public class ReceptionController {

    private final ReceptionService receptionService;
    private final AppointmentService appointmentService;
    private final QueueService queueService;

    public ReceptionController(ReceptionService receptionService, AppointmentService appointmentService,
                               QueueService queueService) {
        this.receptionService = receptionService;
        this.appointmentService = appointmentService;
        this.queueService = queueService;
    }

    @GetMapping("/dashboard")
    public ReceptionDashboard dashboard() {
        return receptionService.dashboard();
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a walk-in patient (they receive a set-password link)")
    public PatientProfileResponse register(@Valid @RequestBody WalkInRegistrationRequest request) {
        return receptionService.registerWalkIn(CurrentUser.get(), request);
    }

    @GetMapping("/patients")
    @Operation(summary = "Search active patients by name, e-mail, phone or patient ID")
    public PageResponse<PatientProfileResponse> patients(@RequestParam(required = false) String search,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return receptionService.searchPatients(search, page, size);
    }

    @GetMapping("/patients/{id}")
    public PatientProfileResponse patient(@PathVariable Long id) {
        return receptionService.getPatient(id);
    }

    @PatchMapping("/patients/{id}/contact")
    @Operation(summary = "Update a patient's phone, address and emergency contact")
    public PatientProfileResponse updateContact(@PathVariable Long id, @Valid @RequestBody PatientContactUpdateRequest request) {
        return receptionService.updateContact(id, request);
    }

    @GetMapping("/patients/{id}/appointments")
    public List<AppointmentResponse> patientAppointments(@PathVariable Long id) {
        return appointmentService.forPatient(CurrentUser.get(), id);
    }

    @GetMapping("/appointments")
    @Operation(summary = "Daily appointment calendar (default today)")
    public List<AppointmentResponse> day(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam(required = false) Long doctorId,
                                         @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.dayList(CurrentUser.get(), date, doctorId, status);
    }

    @GetMapping("/queue")
    @Operation(summary = "Queue board (default today), ordered by doctor and token")
    public List<QueueEntryResponse> queue(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                          @RequestParam(required = false) Long doctorId) {
        return queueService.board(date, doctorId);
    }

    @PatchMapping("/queue/{entryId}/skip")
    public QueueEntryResponse skip(@PathVariable Long entryId) {
        return queueService.skip(entryId);
    }

    @PatchMapping("/queue/{entryId}/requeue")
    public QueueEntryResponse requeue(@PathVariable Long entryId) {
        return queueService.requeue(entryId);
    }
}
