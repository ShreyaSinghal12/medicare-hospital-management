package com.medicare.dto.appointment;

import java.util.List;

/** Doctor dashboard: today's list and counts for today / this week. */
public record DoctorDashboardSummary(
        long todayTotal,
        long todayPending,
        long todayCompleted,
        long weekTotal,
        long upcomingScheduled,
        List<AppointmentResponse> today) {
}
