package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.medicare.entity.Appointment;
import com.medicare.entity.Consultation;
import com.medicare.entity.Doctor;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.NotificationType;
import com.medicare.enums.Role;
import com.medicare.notification.NotificationService;
import com.medicare.notification.ReminderService;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.DoctorAvailabilityRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.service.HospitalClock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

/** Phase 11: reminders (upcoming and follow-up), e-mail preferences, admin reminder settings and next-slot hints. */
class ReminderIntegrationTest extends IntegrationTestBase {

    @Autowired ReminderService reminderService;
    @Autowired NotificationService notificationService;
    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired DoctorAvailabilityRepository availabilityRepository;
    @Autowired HospitalClock clock;
    @Autowired JdbcTemplate jdbc;

    ZoneId zone;
    Doctor doctor;
    Patient patient;
    /** A day far enough ahead that other tests' appointments do not share the reminder window. */
    LocalDate day;

    @BeforeEach
    void setUp() {
        zone = clock.zone();
        doctor = createDoctor("Dr Remind " + unique("r").replaceAll("[^a-z]", "q"), createDepartment(unique("Rem")), "Physician", "300");
        patient = newPatient("Rina Reminder");
        day = clock.today().plusDays(200 + java.util.concurrent.ThreadLocalRandom.current().nextInt(300));
    }

    Patient newPatient(String name) {
        User u = userRepository.save(new User(uniqueEmail("rp"), passwordHash(), name, "+91 90000 88888", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("R").toUpperCase().substring(0, 10), LocalDate.of(1991, 1, 1)));
    }

    Appointment appointment(Patient p, LocalDate date, LocalTime start, Instant createdAt) {
        Appointment a = appointmentRepository.saveAndFlush(new Appointment(unique("APT"), p, doctor, null, date, start,
                start.plusMinutes(15), AppointmentType.NEW, "Check-up", p.getUser().getId()));
        jdbc.update("UPDATE appointments SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), a.getId());
        return a;
    }

    Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(zone).toInstant();
    }

    int notifications(Patient p, NotificationType type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ?", Integer.class,
                p.getUser().getId(), type.name());
    }

    String lastMessage(Patient p, NotificationType type) {
        return jdbc.queryForObject("SELECT message FROM notifications WHERE user_id = ? AND type = ? ORDER BY id DESC LIMIT 1",
                String.class, p.getUser().getId(), type.name());
    }

    @Test
    void upcomingRemindersAreSentOnceInsideTheWindowAndNotAtNight() {
        Instant longAgo = at(day.minusDays(5), 10, 0);
        Appointment a = appointment(patient, day, LocalTime.of(10, 0), longAgo);

        // 30 h before: outside the 24 h window
        reminderService.runDue(at(day.minusDays(2), 4 + 12, 0));
        assertThat(notifications(patient, NotificationType.APPOINTMENT_REMINDER)).isZero();
        // 22:30 the evening before: inside the window but quiet hours
        assertThat(reminderService.runDue(at(day.minusDays(1), 22, 30)).quietHours()).isTrue();
        assertThat(notifications(patient, NotificationType.APPOINTMENT_REMINDER)).isZero();
        // 11:00 the day before: due
        reminderService.runDue(at(day.minusDays(1), 11, 0));
        assertThat(notifications(patient, NotificationType.APPOINTMENT_REMINDER)).isEqualTo(1);
        assertThat(lastMessage(patient, NotificationType.APPOINTMENT_REMINDER)).contains(a.getReferenceNo()).contains("10:00");
        // later runs do not repeat it
        reminderService.runDue(at(day.minusDays(1), 11, 15));
        reminderService.runDue(at(day, 8, 30));
        assertThat(notifications(patient, NotificationType.APPOINTMENT_REMINDER)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment_reminders WHERE appointment_id = ?", String.class, a.getId()))
                .isEqualTo("SENT");

        // a new time (reschedule) gets its own reminder
        jdbc.update("UPDATE appointments SET start_time = '16:00:00', end_time = '16:15:00' WHERE id = ?", a.getId());
        reminderService.runDue(at(day, 9, 0));
        assertThat(notifications(patient, NotificationType.APPOINTMENT_REMINDER)).isEqualTo(2);
    }

    @Test
    void noReminderForLateBookingsCancelledAppointmentsOrInactiveAccounts() {
        Patient late = newPatient("Lata Latebooker");
        appointment(late, day, LocalTime.of(12, 0), at(day.minusDays(1), 14, 0));       // booked 22 h before
        Patient cancelled = newPatient("Carl Cancelled");
        Appointment c = appointment(cancelled, day, LocalTime.of(12, 15), at(day.minusDays(5), 9, 0));
        jdbc.update("UPDATE appointments SET status = 'CANCELLED' WHERE id = ?", c.getId());
        Patient inactive = newPatient("Ivy Inactive");
        appointment(inactive, day, LocalTime.of(12, 30), at(day.minusDays(5), 9, 0));
        jdbc.update("UPDATE users SET active = FALSE WHERE id = ?", inactive.getUser().getId());

        reminderService.runDue(at(day.minusDays(1), 15, 0));
        for (Patient p : List.of(late, cancelled, inactive)) {
            assertThat(notifications(p, NotificationType.APPOINTMENT_REMINDER)).as(p.getUser().getFullName()).isZero();
        }
    }

    @Test
    void followUpReminderIncludesTheNextFreeSlotAndIsSkippedWhenAlreadyBooked() {
        for (int d = 1; d <= 7; d++) {
            DoctorAvailability av = new DoctorAvailability(doctor.getId(), d);
            av.update(LocalTime.of(9, 0), LocalTime.of(17, 0), null, null, true);
            availabilityRepository.save(av);
        }
        LocalDate visitDay = clock.today().minusDays(10);
        Appointment visit = appointment(patient, visitDay, LocalTime.of(10, 0), at(visitDay.minusDays(3), 9, 0));
        Consultation c = consultationRepository.saveAndFlush(new Consultation(visit));
        LocalDate followUp = clock.today().plusDays(1);
        jdbc.update("UPDATE consultations SET status = 'FINALIZED', diagnosis = 'Fictional', follow_up_date = ? WHERE id = ?",
                followUp, c.getId());

        reminderService.runDue(at(clock.today(), 10, 0));
        assertThat(notifications(patient, NotificationType.FOLLOW_UP_REMINDER)).isEqualTo(1);
        assertThat(lastMessage(patient, NotificationType.FOLLOW_UP_REMINDER))
                .contains("asked to see you again").contains("The next free time is");
        reminderService.runDue(at(clock.today(), 12, 0));
        assertThat(notifications(patient, NotificationType.FOLLOW_UP_REMINDER)).isEqualTo(1);

        // another patient who already booked the follow-up gets no reminder
        Patient booked = newPatient("Bob Booked");
        Appointment v2 = appointment(booked, visitDay, LocalTime.of(11, 0), at(visitDay.minusDays(3), 9, 0));
        Consultation c2 = consultationRepository.saveAndFlush(new Consultation(v2));
        jdbc.update("UPDATE consultations SET status = 'FINALIZED', diagnosis = 'Fictional', follow_up_date = ? WHERE id = ?", followUp, c2.getId());
        appointment(booked, followUp, LocalTime.of(9, 30), at(clock.today().minusDays(1), 9, 0));
        reminderService.runDue(at(clock.today(), 13, 0));
        assertThat(notifications(booked, NotificationType.FOLLOW_UP_REMINDER)).isZero();
    }

    @Test
    void emailPreferencesPerCategoryAndSecurityEmailsAlwaysSent() throws Exception {
        MockHttpSession s = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/account/notification-preferences").session(s))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.categories.REMINDERS").value(true))
                .andExpect(jsonPath("$.categories.SECURITY").doesNotExist());
        mockMvc.perform(put("/api/account/notification-preferences").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true,\"categories\":{\"BILLING\":false,\"SECURITY\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.BILLING").value(false))
                .andExpect(jsonPath("$.categories.REMINDERS").value(true));
        mockMvc.perform(put("/api/account/notification-preferences").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":true,\"categories\":{\"NOPE\":false}}")).andExpect(status().isBadRequest());

        Long uid = patient.getUser().getId();
        var billing = notificationService.notify(uid, NotificationType.INVOICE_GENERATED, "t", "m", null, null, true);
        var results = notificationService.notify(uid, NotificationType.LAB_REPORT_READY, "t", "m", null, null, true);
        assertThat(billing.getEmailStatus().name()).isEqualTo("SKIPPED");
        assertThat(results.getEmailStatus().name()).isEqualTo("SENT");

        mockMvc.perform(put("/api/account/notification-preferences").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":false,\"categories\":{}}")).andExpect(jsonPath("$.emailEnabled").value(false));
        assertThat(notificationService.notify(uid, NotificationType.LAB_REPORT_READY, "t", "m", null, null, true).getEmailStatus().name())
                .isEqualTo("SKIPPED");
        assertThat(notificationService.notify(uid, NotificationType.ACCOUNT, "Password changed", "m", null, null, true).getEmailStatus().name())
                .isEqualTo("SENT");
        assertThat(notificationService.notify(uid, NotificationType.OPERATIONAL, "Staff", "m", null, null, true).getEmailStatus().name())
                .isEqualTo("SKIPPED");
    }

    @Test
    void adminReminderSettingsRunAndLog() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        Integer oldHours = jdbc.queryForObject("SELECT reminder_hours_before FROM hospital_settings WHERE id = 1", Integer.class);
        Boolean oldEmail = jdbc.queryForObject("SELECT email_reminders_enabled FROM hospital_settings WHERE id = 1", Boolean.class);
        try {
            mockMvc.perform(put("/api/admin/settings/reminders").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"emailRemindersEnabled\":true,\"reminderHoursBefore\":0}")).andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/admin/settings/reminders").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailRemindersEnabled\":false,\"reminderHoursBefore\":48}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.reminderHoursBefore").value(48));

            // with 48 h, an appointment 40 h ahead is now due; e-mail is off so the reminder is in-app only
            appointment(patient, day, LocalTime.of(14, 0), at(day.minusDays(5), 9, 0));
            reminderService.runDue(at(day.minusDays(2), 22, 0 + 0));   // quiet hours
            reminderService.runDue(at(day.minusDays(2), 20, 30));      // 41.5 h before
            assertThat(notifications(patient, NotificationType.APPOINTMENT_REMINDER)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT email_status FROM notifications WHERE user_id = ? AND type = 'APPOINTMENT_REMINDER'",
                    String.class, patient.getUser().getId())).isEqualTo("NOT_REQUESTED");
        } finally {
            jdbc.update("UPDATE hospital_settings SET reminder_hours_before = ?, email_reminders_enabled = ? WHERE id = 1", oldHours, oldEmail);
        }
        mockMvc.perform(post("/api/admin/reminders/run").with(csrf()).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quietHours").isBoolean());
        mockMvc.perform(get("/api/admin/reminders").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        for (Role role : new Role[]{Role.RECEPTIONIST, Role.DOCTOR, Role.PATIENT}) {
            MockHttpSession other = loginAs(role);
            mockMvc.perform(post("/api/admin/reminders/run").with(csrf()).session(other)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/settings/reminders").session(other)).andExpect(status().isForbidden());
        }
    }

    @Test
    void cancellationByStaffSuggestsTheNextFreeSlot() throws Exception {
        for (int d = 1; d <= 7; d++) {
            DoctorAvailability av = new DoctorAvailability(doctor.getId(), d);
            av.update(LocalTime.of(0, 15), LocalTime.of(23, 45), null, null, true);
            availabilityRepository.save(av);
        }
        Appointment a = appointment(patient, clock.today().plusDays(3), LocalTime.of(10, 0), Instant.now());
        mockMvc.perform(patch("/api/appointments/{id}/cancel", a.getId()).with(csrf()).session(loginAs(Role.RECEPTIONIST))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Doctor called away (fictional)\"}"))
                .andExpect(status().isOk());
        assertThat(lastMessage(patient, NotificationType.APPOINTMENT_CANCELLED)).contains("The next free time with");
        mockMvc.perform(get("/api/notifications").session(login(patient.getUser().getEmail())))
                .andExpect(jsonPath("$.content[0].message", containsString("next free time")));
    }
}
