package com.medicare.dto.schedule;

import java.util.List;

/**
 * @param flaggedAppointments number of future appointments that no longer fit the schedule and were
 *                            flagged "needs reschedule" by this change (only set on updates)
 */
public record WeeklyScheduleResponse(
        Long doctorId,
        String doctorName,
        int slotDurationMinutes,
        List<DayAvailabilityDto> days,
        Integer flaggedAppointments) {
}
