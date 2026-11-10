package com.medicare.mapper;

import com.medicare.dto.appointment.AppointmentHistoryResponse;
import com.medicare.dto.appointment.AppointmentResponse;
import com.medicare.entity.Appointment;
import com.medicare.entity.AppointmentStatusHistory;
import com.medicare.entity.QueueEntry;

public final class AppointmentMapper {

    private AppointmentMapper() {
    }

    /**
     * @param showReason false for front-desk viewers: the visit reason can contain symptoms
     * @param queue      queue entry if the patient has checked in, else null
     */
    public static AppointmentResponse toResponse(Appointment a, boolean showReason, QueueEntry queue,
                                                 boolean canCancel, boolean canReschedule) {
        var p = a.getPatient();
        var d = a.getDoctor();
        return new AppointmentResponse(a.getId(), a.getReferenceNo(), p.getId(), p.getUser().getFullName(),
                p.getPatientCode(), d.getId(), d.getUser().getFullName(), d.getSpecialization(),
                d.getDepartment().getName(), d.getConsultationFee(), a.getAppointmentDate(), a.getStartTime(),
                a.getEndTime(), a.getAppointmentType(), showReason ? a.getReason() : null, a.getStatus(),
                a.isNeedsReschedule(), a.getFollowUpOfId(), a.getRescheduleCount(), a.getCancellationReason(),
                a.getCancelledAt(), a.getCreatedAt(), queue == null ? null : queue.getTokenNumber(),
                queue == null ? null : queue.getStatus(), canCancel, canReschedule);
    }

    public static AppointmentHistoryResponse toHistory(AppointmentStatusHistory h) {
        return new AppointmentHistoryResponse(h.getFromStatus(), h.getToStatus(), h.getAction(), h.getDetails(),
                h.getChangedAt());
    }
}
