package com.medicare.controller;

import java.time.LocalDate;
import java.util.List;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.department.DepartmentResponse;
import com.medicare.dto.doctor.DoctorDetailResponse;
import com.medicare.dto.doctor.DoctorSummaryResponse;
import com.medicare.dto.schedule.DaySlotsResponse;
import com.medicare.dto.schedule.NextAvailableResponse;
import com.medicare.dto.schedule.WeeklyScheduleResponse;
import com.medicare.service.DepartmentService;
import com.medicare.service.DoctorService;
import com.medicare.service.ScheduleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public (no login) doctor directory and department list. Only active records are shown. */
@RestController
@Tag(name = "Doctor directory", description = "Public doctor search and profiles")
public class DoctorDirectoryController {

    private final DoctorService doctorService;
    private final DepartmentService departmentService;
    private final ScheduleService scheduleService;

    public DoctorDirectoryController(DoctorService doctorService, DepartmentService departmentService,
                                     ScheduleService scheduleService) {
        this.doctorService = doctorService;
        this.departmentService = departmentService;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/api/doctors/{id}/schedule")
    @Operation(summary = "Weekly working hours of a doctor")
    public WeeklyScheduleResponse schedule(@PathVariable Long id) {
        return scheduleService.getPublicWeekly(id);
    }

    @GetMapping("/api/doctors/{id}/slots")
    @Operation(summary = "Generated slots for one date (AVAILABLE, BOOKED, PAST or UNAVAILABLE)")
    public DaySlotsResponse slots(@PathVariable Long id,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleService.slotsFor(id, date);
    }

    @GetMapping("/api/doctors/{id}/next-available")
    @Operation(summary = "Earliest bookable slot (204 if none within the booking window)")
    public ResponseEntity<NextAvailableResponse> nextAvailable(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        NextAvailableResponse next = scheduleService.nextAvailable(id, from);
        return next == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(next);
    }

    @GetMapping("/api/doctors")
    @Operation(summary = "Search active doctors by name/specialty, department and specialization")
    public PageResponse<DoctorSummaryResponse> search(@RequestParam(required = false) String search,
                                                      @RequestParam(required = false) Long departmentId,
                                                      @RequestParam(required = false) String specialization,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "12") int size) {
        return doctorService.searchPublic(search, departmentId, specialization, page, size);
    }

    @GetMapping("/api/doctors/specializations")
    @Operation(summary = "Distinct specializations of active doctors (for filters)")
    public List<String> specializations() {
        return doctorService.specializations();
    }

    @GetMapping("/api/doctors/{id}")
    @Operation(summary = "Public doctor profile")
    public DoctorDetailResponse get(@PathVariable Long id) {
        return doctorService.getPublic(id);
    }

    @GetMapping("/api/public/departments")
    @Operation(summary = "Active departments with their number of active doctors")
    public List<DepartmentResponse> departments() {
        return departmentService.listActive();
    }
}
