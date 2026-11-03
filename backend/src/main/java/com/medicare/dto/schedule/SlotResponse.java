package com.medicare.dto.schedule;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/** One generated slot. status: AVAILABLE, BOOKED, PAST or UNAVAILABLE (leave/blocked). */
public record SlotResponse(
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        String status) {

    public boolean isAvailable() {
        return "AVAILABLE".equals(status);
    }
}
