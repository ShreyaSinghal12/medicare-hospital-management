package com.medicare.dto.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicare.enums.LeaveStatus;
import com.medicare.enums.LeaveType;

public record LeaveResponse(
        Long id,
        Long doctorId,
        String doctorName,
        String departmentName,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        String reason,
        LeaveStatus status,
        Instant reviewedAt,
        Instant createdAt,
        Integer affectedAppointments) {
}
