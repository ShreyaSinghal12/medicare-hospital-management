package com.medicare.dto.schedule;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.medicare.enums.LeaveType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Leave (whole days) or UNAVAILABLE (startTime–endTime on each day of the range). */
public record LeaveRequest(
        @NotNull LeaveType leaveType,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @Size(max = 500) String reason) {
}
