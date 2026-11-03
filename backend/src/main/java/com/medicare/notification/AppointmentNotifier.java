package com.medicare.notification;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.medicare.entity.Appointment;
import com.medicare.entity.DoctorLeave;
import com.medicare.enums.LeaveStatus;
import com.medicare.enums.NotificationType;

import org.springframework.stereotype.Component;

/**
 * Appointment-related notifications. Messages contain only scheduling information
 * (doctor, date, time, reference) — never the reason for the visit or clinical details.
 * Patients get in-app + optional e-mail; doctors get in-app notifications.
 */
@Component
public class AppointmentNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notificationService;

    public AppointmentNotifier(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void booked(Appointment a) {
        notifyPatient(a, NotificationType.APPOINTMENT_CONFIRMED, "Appointment confirmed",
                "Your appointment with " + doctor(a) + " on " + when(a) + " is confirmed. Reference " + a.getReferenceNo() + ".",
                "APPT_BOOKED:" + a.getId());
        notifyDoctor(a, NotificationType.APPOINTMENT_CONFIRMED, "New appointment",
                a.getPatient().getUser().getFullName() + " booked " + when(a) + ".", "APPT_BOOKED_DR:" + a.getId());
    }

    public void cancelled(Appointment a, boolean byPatient) {
        notifyPatient(a, NotificationType.APPOINTMENT_CANCELLED, "Appointment cancelled",
                "Your appointment with " + doctor(a) + " on " + when(a) + " (" + a.getReferenceNo() + ") has been cancelled"
                        + (byPatient ? "." : " by the hospital. Please book a new time if needed."),
                "APPT_CANCELLED:" + a.getId());
        notifyDoctor(a, NotificationType.APPOINTMENT_CANCELLED, "Appointment cancelled",
                a.getPatient().getUser().getFullName() + "'s appointment on " + when(a) + " was cancelled.",
                "APPT_CANCELLED_DR:" + a.getId());
    }

    public void rescheduled(Appointment a, String previousWhen) {
        String key = "APPT_RESCHEDULED:" + a.getId() + ":" + a.getRescheduleCount();
        notifyPatient(a, NotificationType.APPOINTMENT_RESCHEDULED, "Appointment rescheduled",
                "Your appointment with " + doctor(a) + " moved from " + previousWhen + " to " + when(a)
                        + ". Reference " + a.getReferenceNo() + ".", key);
        notifyDoctor(a, NotificationType.APPOINTMENT_RESCHEDULED, "Appointment moved",
                a.getPatient().getUser().getFullName() + " moved from " + previousWhen + " to " + when(a) + ".", key + ":DR");
    }

    public void needsReschedule(Appointment a) {
        notifyPatient(a, NotificationType.APPOINTMENT_RESCHEDULED, "Please choose a new time",
                doctor(a) + " is no longer available on " + when(a) + " (" + a.getReferenceNo()
                        + "). Your booking is kept — please reschedule it or contact reception.",
                "APPT_FLAGGED:" + a.getId() + ":" + a.getAppointmentDate() + "T" + a.getStartTime());
    }

    public void leaveDecided(DoctorLeave leave) {
        boolean approved = leave.getStatus() == LeaveStatus.APPROVED;
        notificationService.notify(leave.getDoctor().getUser().getId(), NotificationType.OPERATIONAL,
                "Leave request " + (approved ? "approved" : "rejected"),
                "Your " + leave.getLeaveType().name().toLowerCase(Locale.ROOT) + " request for " + leave.getStartDate()
                        + (leave.getEndDate().equals(leave.getStartDate()) ? "" : " to " + leave.getEndDate())
                        + " was " + (approved ? "approved." : "rejected."),
                "/doctor/availability", "LEAVE_DECIDED:" + leave.getId(), false);
    }

    public static String when(Appointment a) {
        return DATE.format(a.getAppointmentDate()) + " at " + TIME.format(a.getStartTime());
    }

    private static String doctor(Appointment a) {
        return a.getDoctor().getUser().getFullName();
    }

    private void notifyPatient(Appointment a, NotificationType type, String title, String message, String key) {
        notificationService.notify(a.getPatient().getUser().getId(), type, title, message,
                "/patient/appointments/" + a.getId(), key, true);
    }

    private void notifyDoctor(Appointment a, NotificationType type, String title, String message, String key) {
        notificationService.notify(a.getDoctor().getUser().getId(), type, title, message,
                "/doctor/appointments", key, false);
    }
}
