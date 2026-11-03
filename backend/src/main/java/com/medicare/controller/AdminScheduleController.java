package com.medicare.controller;

import jakarta.validation.Valid;

import com.medicare.dto.common.PageResponse;
import com.medicare.dto.schedule.LeaveDecisionRequest;
import com.medicare.dto.schedule.LeaveRequest;
import com.medicare.dto.schedule.LeaveResponse;
import com.medicare.dto.schedule.WeeklyScheduleRequest;
import com.medicare.dto.schedule.WeeklyScheduleResponse;
import com.medicare.enums.LeaveStatus;
import com.medicare.security.CurrentUser;
import com.medicare.service.ScheduleService;

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

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin: schedules & leave")
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    public AdminScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/doctors/{id}/availability")
    public WeeklyScheduleResponse availability(@PathVariable Long id) {
        return scheduleService.getWeekly(id);
    }

    @PutMapping("/doctors/{id}/availability")
    @Operation(summary = "Set a doctor's weekly hours; affected bookings are flagged for rescheduling")
    public WeeklyScheduleResponse updateAvailability(@PathVariable Long id, @Valid @RequestBody WeeklyScheduleRequest request) {
        return scheduleService.updateWeekly(id, request);
    }

    @PostMapping("/doctors/{id}/leave")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record leave for a doctor (approved immediately)")
    public LeaveResponse recordLeave(@PathVariable Long id, @Valid @RequestBody LeaveRequest request) {
        return scheduleService.requestLeave(id, request, true, CurrentUser.id());
    }

    @GetMapping("/leave")
    public PageResponse<LeaveResponse> leave(@RequestParam(required = false) LeaveStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return scheduleService.leaveForAdmin(status, page, size);
    }

    @PatchMapping("/leave/{id}/decision")
    @Operation(summary = "Approve or reject a pending leave request")
    public LeaveResponse decide(@PathVariable Long id, @Valid @RequestBody LeaveDecisionRequest request) {
        return scheduleService.decide(id, request.approve(), CurrentUser.id());
    }
}
