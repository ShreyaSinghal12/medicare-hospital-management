package com.medicare.dto.schedule;

import java.time.LocalDate;
import java.util.List;

/**
 * Slots of one doctor for one date. {@code message} explains an empty day
 * (not a working day, on leave, outside the booking window, in the past).
 */
public record DaySlotsResponse(
        Long doctorId,
        LocalDate date,
        int slotDurationMinutes,
        boolean working,
        String message,
        long availableCount,
        List<SlotResponse> slots) {
}
