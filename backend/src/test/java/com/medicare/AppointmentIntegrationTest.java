package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.medicare.dto.appointment.BookAppointmentRequest;
import com.medicare.entity.Appointment;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.Role;
import com.medicare.exception.ConflictException;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.DoctorAvailabilityRepository;
import com.medicare.repository.NotificationRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.security.AppUserPrincipal;
import com.medicare.service.AppointmentService;
import com.medicare.service.HospitalSettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/**
 * Phase 4: scheduling, booking, conflicts, cancellation, rescheduling, authorisation,
 * leave/schedule flagging and concurrent booking against the real MySQL database.
 */
class AppointmentIntegrationTest extends IntegrationTestBase {

    @Autowired DoctorAvailabilityRepository availabilityRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AppointmentService appointmentService;
    @Autowired HospitalSettingsService settingsService;

    Doctor doctor;
    LocalDate date;

    /** Fresh doctor working every day 09:00–17:00 (break 13:00–14:00), 15-minute slots; date = 3 days ahead. */
    @BeforeEach
    void setUp() {
        Department dept = createDepartment(unique("Appt"));
        doctor = createDoctor("Dr Slot " + unique("x").replaceAll("[^a-z]", "q"), dept, "Physician", "500.00");
        for (int d = 1; d <= 7; d++) {
            DoctorAvailability a = new DoctorAvailability(doctor.getId(), d);
            a.update(LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(13, 0), LocalTime.of(14, 0), true);
            availabilityRepository.save(a);
        }
        date = LocalDate.now(ZoneId.of(settingsService.getSettings().getTimeZone())).plusDays(3);
    }

    Patient createPatient() {
        User u = userRepository.save(new User(uniqueEmail("pt"), passwordHash(), "Pat Booker", "+91 90000 33333", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("P").toUpperCase().substring(0, 10), LocalDate.of(1990, 1, 1)));
    }

    String bookJson(Long doctorId, LocalDate d, String time) {
        return "{\"doctorId\":%d,\"date\":\"%s\",\"startTime\":\"%s\",\"reason\":\"Routine check-up\"}".formatted(doctorId, d, time);
    }

    long book(MockHttpSession session, String time) throws Exception {
        String body = mockMvc.perform(post("/api/appointments").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(doctor.getId(), date, time)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    // ------------------------------------------------------------- slots

    @Test
    void slotsAreGeneratedFromScheduleExcludingBreak() throws Exception {
        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(true))
                .andExpect(jsonPath("$.availableCount").value(28))       // 4h morning + 3h afternoon in 15-min steps
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.slots[*].startTime", not(hasItem("13:00"))))
                .andExpect(jsonPath("$.slots[*].startTime", hasItem("14:00")));

        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.plusDays(400).toString()))
                .andExpect(jsonPath("$.slots").isEmpty())
                .andExpect(jsonPath("$.message", matchesPattern("Bookings open \\d+ days in advance.")));

        mockMvc.perform(get("/api/doctors/{id}/next-available", doctor.getId()).param("from", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andExpect(jsonPath("$.startTime").value("09:00"));
    }

    // ------------------------------------------------------------- booking

    @Test
    void patientBooksAndTheSlotCannotBeBookedAgain() throws Exception {
        Patient p1 = createPatient();
        Patient p2 = createPatient();
        MockHttpSession s1 = login(p1.getUser().getEmail());
        MockHttpSession s2 = login(p2.getUser().getEmail());

        mockMvc.perform(post("/api/appointments").with(csrf()).session(s1)
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(doctor.getId(), date, "09:30")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceNo", matchesPattern("APT-\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.endTime").value("09:45"))
                .andExpect(jsonPath("$.canCancel").value(true));

        mockMvc.perform(post("/api/appointments").with(csrf()).session(s2)
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(doctor.getId(), date, "09:30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_ALREADY_BOOKED"));

        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(jsonPath("$.slots[?(@.startTime == '09:30')].status").value("BOOKED"));
        assertThat(notificationRepository.countByUserIdAndReadFalse(p1.getUser().getId())).isEqualTo(1);
        assertThat(notificationRepository.countByUserIdAndReadFalse(doctor.getUser().getId())).isEqualTo(1);
    }

    @Test
    void invalidTimesPastDatesAndOverlappingPatientBookingsAreRejected() throws Exception {
        Patient p = createPatient();
        MockHttpSession s = login(p.getUser().getEmail());
        mockMvc.perform(post("/api/appointments").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(doctor.getId(), date, "09:07")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
        mockMvc.perform(post("/api/appointments").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(doctor.getId(), date.minusDays(10), "09:00")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.message").value("This date is in the past."));
        mockMvc.perform(post("/api/appointments").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(doctor.getId(), date, "13:15")))
                .andExpect(status().isUnprocessableContent());

        book(s, "10:00");
        Doctor other = createDoctor("Dr Other " + unique("y").replaceAll("[^a-z]", "q"), doctor.getDepartment(), "Physician", "300");
        DoctorAvailability a = new DoctorAvailability(other.getId(), date.getDayOfWeek().getValue());
        a.update(LocalTime.of(9, 0), LocalTime.of(12, 0), null, null, true);
        availabilityRepository.save(a);
        mockMvc.perform(post("/api/appointments").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(other.getId(), date, "10:00")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PATIENT_TIME_CONFLICT"));
    }

    @Test
    void patientsCannotBookForOthersAndDoctorsCannotBook() throws Exception {
        Patient p = createPatient();
        Patient victim = createPatient();
        MockHttpSession s = login(p.getUser().getEmail());
        mockMvc.perform(post("/api/appointments").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(doctor.getId(), date, "11:00").replace("{", "{\"patientId\":" + victim.getId() + ",")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/appointments").with(csrf()).session(loginAs(Role.DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(doctor.getId(), date, "11:00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void receptionistBooksForAPatient() throws Exception {
        Patient p = createPatient();
        mockMvc.perform(post("/api/appointments").with(csrf()).session(loginAs(Role.RECEPTIONIST))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookJson(doctor.getId(), date, "15:00").replace("{", "{\"patientId\":" + p.getId() + ",")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(p.getId()));
    }

    // ------------------------------------------------------------- cancel / reschedule

    @Test
    void cancellationReleasesTheSlotAndCannotBeRepeated() throws Exception {
        Patient p1 = createPatient();
        Patient p2 = createPatient();
        MockHttpSession s1 = login(p1.getUser().getEmail());
        long id = book(s1, "11:00");

        mockMvc.perform(patch("/api/appointments/{id}/cancel", id).with(csrf()).session(s1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Feeling better\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canCancel").value(false));
        mockMvc.perform(patch("/api/appointments/{id}/cancel", id).with(csrf()).session(s1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"again\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        book(login(p2.getUser().getEmail()), "11:00"); // freed slot can be booked again
    }

    @Test
    void rescheduleMovesTheBookingAtomicallyAndRecordsHistory() throws Exception {
        Patient p = createPatient();
        MockHttpSession s = login(p.getUser().getEmail());
        long id = book(s, "09:00");

        mockMvc.perform(patch("/api/appointments/{id}/reschedule", id).with(csrf()).session(s)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"%s\",\"startTime\":\"16:00\",\"reason\":\"Clash with work\"}".formatted(date.plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date.plusDays(1).toString()))
                .andExpect(jsonPath("$.startTime").value("16:00"))
                .andExpect(jsonPath("$.rescheduleCount").value(1));

        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(jsonPath("$.slots[?(@.startTime == '09:00')].status").value("AVAILABLE"));
        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.plusDays(1).toString()))
                .andExpect(jsonPath("$.slots[?(@.startTime == '16:00')].status").value("BOOKED"));
        mockMvc.perform(get("/api/appointments/{id}/history", id).session(s))
                .andExpect(jsonPath("$[*].action", hasItem("BOOKED")))
                .andExpect(jsonPath("$[*].action", hasItem("RESCHEDULED")));

        // rescheduling onto a slot that is taken fails and leaves the appointment unchanged
        Patient other = createPatient();
        book(login(other.getUser().getEmail()), "10:00");
        mockMvc.perform(patch("/api/appointments/{id}/reschedule", id).with(csrf()).session(s)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"%s\",\"startTime\":\"10:00\"}".formatted(date)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/appointments/{id}", id).session(s))
                .andExpect(jsonPath("$.startTime").value("16:00"));
    }

    // ------------------------------------------------------------- authorisation

    @Test
    void appointmentsAreVisibleOnlyToTheirPatientDoctorAndFrontDesk() throws Exception {
        Patient owner = createPatient();
        long id = book(login(owner.getUser().getEmail()), "12:00");

        MockHttpSession stranger = login(createPatient().getUser().getEmail());
        mockMvc.perform(get("/api/appointments/{id}", id).session(stranger)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/appointments/{id}/cancel", id).with(csrf()).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"malicious\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/appointments/{id}", id).session(login(doctor.getUser().getEmail())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/appointments/{id}", id).session(loginAs(Role.DOCTOR)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/appointments/{id}", id).session(loginAs(Role.RECEPTIONIST)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/doctor/appointments").param("from", date.toString()).param("to", date.toString())
                        .session(login(doctor.getUser().getEmail())))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));
    }

    @Test
    void noShowCannotBeRecordedBeforeTheAppointmentStarts() throws Exception {
        long id = book(login(createPatient().getUser().getEmail()), "14:15");
        mockMvc.perform(patch("/api/appointments/{id}/no-show", id).with(csrf()).session(login(doctor.getUser().getEmail())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOO_EARLY"));
    }

    // ------------------------------------------------------------- schedule changes & leave

    @Test
    void approvedLeaveFlagsExistingAppointmentsInsteadOfCancellingThem() throws Exception {
        Patient p = createPatient();
        MockHttpSession ps = login(p.getUser().getEmail());
        long id = book(ps, "14:30");
        MockHttpSession doc = login(doctor.getUser().getEmail());

        String body = mockMvc.perform(post("/api/doctor/leave").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveType\":\"LEAVE\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Conference\"}".formatted(date, date)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long leaveId = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));

        // pending leave does not block slots yet
        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(jsonPath("$.availableCount").value(27));

        mockMvc.perform(patch("/api/admin/leave/{id}/decision", leaveId).with(csrf()).session(loginAs(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.affectedAppointments").value(1));

        mockMvc.perform(get("/api/appointments/{id}", id).session(ps))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.needsReschedule").value(true))
                .andExpect(jsonPath("$.canReschedule").value(true));
        mockMvc.perform(get("/api/doctors/{id}/slots", doctor.getId()).param("date", date.toString()))
                .andExpect(jsonPath("$.slots").isEmpty())
                .andExpect(jsonPath("$.message").value("The doctor is on leave on this day."));
    }

    @Test
    void weeklyScheduleChangeFlagsAppointmentsAndIsValidated() throws Exception {
        long id = book(login(createPatient().getUser().getEmail()), "16:30");
        MockHttpSession doc = login(doctor.getUser().getEmail());
        StringBuilder days = new StringBuilder();
        for (int d = 1; d <= 7; d++) {
            days.append(d > 1 ? "," : "").append("{\"dayOfWeek\":%d,\"working\":true,\"startTime\":\"09:00\",\"endTime\":\"12:00\"}".formatted(d));
        }
        mockMvc.perform(put("/api/doctor/availability").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[" + days + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flaggedAppointments").value(1))
                .andExpect(jsonPath("$.days[0].endTime").value("12:00"));
        assertThat(appointmentRepository.findById(id).orElseThrow().isNeedsReschedule()).isTrue();

        mockMvc.perform(put("/api/doctor/availability").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":[" + days.toString().replaceFirst("\"endTime\":\"12:00\"", "\"endTime\":\"08:00\"") + "]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_HOURS"));
    }

    // ------------------------------------------------------------- concurrency

    /**
     * Ten different patients try to book the same slot at the same instant (separate threads,
     * separate transactions). Exactly one must succeed; the others get SLOT_ALREADY_BOOKED.
     */
    @Test
    void concurrentBookingsForTheSameSlotYieldExactlyOneAppointment() throws Exception {
        int threads = 10;
        List<AppUserPrincipal> patients = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Patient p = createPatient();
            patients.add(new AppUserPrincipal(p.getUser().getId(), p.getUser().getEmail(), "P", Role.PATIENT,
                    java.time.Instant.now()));
        }
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (AppUserPrincipal actor : patients) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    ready.countDown();
                    go.await();
                    try {
                        appointmentService.book(actor, new BookAppointmentRequest(doctor.getId(), null, date,
                                LocalTime.of(15, 45), "Concurrent test", null));
                        successes.incrementAndGet();
                    } catch (ConflictException e) {
                        assertThat(e.getCode()).isEqualTo("SLOT_ALREADY_BOOKED");
                        conflicts.incrementAndGet();
                    }
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        long stored = appointmentRepository.findOccupying(doctor.getId(), date).stream()
                .filter(a -> a.getStartTime().equals(LocalTime.of(15, 45))).count();
        assertThat(stored).isEqualTo(1);
    }

    /** The database itself rejects a second active appointment at the same doctor/date/time. */
    @Test
    void databaseUniqueKeyIsAnIndependentGuardAgainstDoubleBooking() {
        Patient p1 = createPatient();
        Patient p2 = createPatient();
        appointmentRepository.saveAndFlush(new Appointment(unique("REF"), p1, doctor, null, date, LocalTime.of(16, 0),
                LocalTime.of(16, 15), AppointmentType.NEW, "x", p1.getUser().getId()));
        assertThatThrownBy(() -> appointmentRepository.saveAndFlush(new Appointment(unique("REF"), p2, doctor, null, date,
                LocalTime.of(16, 0), LocalTime.of(16, 15), AppointmentType.NEW, "y", p2.getUser().getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
