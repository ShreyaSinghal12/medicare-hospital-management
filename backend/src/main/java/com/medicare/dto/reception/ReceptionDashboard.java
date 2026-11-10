package com.medicare.dto.reception;

import java.time.LocalDate;
import java.util.List;

/** Today's front-desk overview, computed live from appointments and queue entries. */
public record ReceptionDashboard(
        LocalDate date,
        long totalAppointments,
        long scheduled,
        long waiting,
        long inConsultation,
        long completed,
        long cancelled,
        long noShow,
        long walkIns,
        long patientsRegisteredToday,
        List<DoctorQueueSummary> doctors) {

    public record DoctorQueueSummary(Long doctorId, String doctorName, long appointments, long waiting,
                                     long inConsultation, long completed) {
    }
}
