package com.medicare.controller;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import com.medicare.dto.appointment.AppointmentResponse;
import com.medicare.dto.appointment.DoctorDashboardSummary;
import com.medicare.dto.common.PageResponse;
import com.medicare.dto.schedule.LeaveRequest;
import com.medicare.dto.schedule.LeaveResponse;
import com.medicare.dto.schedule.WeeklyScheduleRequest;
import com.medicare.dto.schedule.WeeklyScheduleResponse;
import com.medicare.enums.AppointmentStatus;
import com.medicare.security.CurrentUser;
import com.medicare.service.AppointmentService;
import com.medicare.service.ScheduleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
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

/** The logged-in doctor's schedule, leave requests and appointments. */
@RestController
@RequestMapping("/api/doctor")
@PreAuthorize("hasRole('DOCTOR')")
@Tag(name = "Doctor: schedule & appointments")
public class DoctorScheduleController {

    private final ScheduleService scheduleService;
    private final AppointmentService appointmentService;

    public DoctorScheduleController(ScheduleService scheduleService, AppointmentService appointmentService) {
        this.scheduleService = scheduleService;
        this.appointmentService = appointmentService;
    }

    private Long doctorId() {
        return scheduleService.doctorIdForUser(CurrentUser.id());
    }

    @GetMapping("/availability")
    public WeeklyScheduleResponse availability() {
        return scheduleService.getWeekly(doctorId());
    }

    @PutMapping("/availability")
    @Operation(summary = "Replace weekly hours; affected future bookings are flagged, never cancelled")
    public WeeklyScheduleResponse updateAvailability(@Valid @RequestBody WeeklyScheduleRequest request) {
        return scheduleService.updateWeekly(doctorId(), request);
    }

    @GetMapping("/leave")
    public List<LeaveResponse> leave() {
        return scheduleService.leaveForDoctor(doctorId());
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Request leave or unavailability (needs admin approval)")
    public LeaveResponse requestLeave(@Valid @RequestBody LeaveRequest request) {
        return scheduleService.requestLeave(doctorId(), request, false, CurrentUser.id());
    }

    @PatchMapping("/leave/{id}/cancel")
    public LeaveResponse cancelLeave(@PathVariable Long id) {
        return scheduleService.cancelLeave(doctorId(), id);
    }

    @GetMapping("/appointments")
    @Operation(summary = "Own appointments, filter by date range and status")
    public PageResponse<AppointmentResponse> appointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return appointmentService.listForDoctor(CurrentUser.get(), from, to, status, page, size);
    }

    @GetMapping("/dashboard")
    public DoctorDashboardSummary dashboard() {
        return appointmentService.doctorDashboard(CurrentUser.get());
    }
}
