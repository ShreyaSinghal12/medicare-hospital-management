package com.medicare.dto.appointment;

/** Counts for the patient dashboard plus the next upcoming appointment (may be null). */
public record PatientAppointmentSummary(
        long total,
        long upcoming,
        long completed,
        long cancelled,
        long noShow,
        AppointmentResponse next) {
}
