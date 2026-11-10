package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import com.medicare.entity.Appointment;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.QueueStatus;
import com.medicare.enums.Role;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.DoctorAvailabilityRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.repository.QueueEntryRepository;
import com.medicare.security.AppUserPrincipal;
import com.medicare.service.HospitalSettingsService;
import com.medicare.service.QueueService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Phase 7: walk-ins, patient directory, front-desk booking, check-in tokens and queue synchronisation. */
class ReceptionIntegrationTest extends IntegrationTestBase {

    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired QueueEntryRepository queueRepository;
    @Autowired DoctorAvailabilityRepository availabilityRepository;
    @Autowired QueueService queueService;
    @Autowired HospitalSettingsService settingsService;

    Doctor doctor;
    LocalDate today;
    MockHttpSession desk;

    @BeforeEach
    void setUp() throws Exception {
        Department dept = createDepartment(unique("Desk"));
        doctor = createDoctor("Dr Queue " + unique("q").replaceAll("[^a-z]", "x"), dept, "Physician", "300");
        today = LocalDate.now(ZoneId.of(settingsService.getSettings().getTimeZone()));
        desk = loginAs(Role.RECEPTIONIST);
    }

    Patient patient() {
        User u = userRepository.save(new User(uniqueEmail("qp"), passwordHash(), "Quinn Patient", "+91 90000 66666", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("Q").toUpperCase().substring(0, 10), LocalDate.of(1990, 1, 1)));
    }

    Appointment appointment(Patient p, LocalDate date, LocalTime start) {
        return appointmentRepository.save(new Appointment(unique("APT"), p, doctor, null, date, start, start.plusMinutes(15),
                AppointmentType.NEW, "Private symptom description", p.getUser().getId()));
    }

    // ------------------------------------------------------------- walk-ins & directory

    @Test
    void receptionRegistersWalkInWhoGetsAnActivationLink() throws Exception {
        String email = uniqueEmail("walkin");
        mockMvc.perform(post("/api/reception/patients").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Wally Walkin","email":"%s","phone":"+91 98000 22222","dateOfBirth":"1970-07-07",
                                 "gender":"MALE","bloodGroup":"A-","address":"Somewhere"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientCode", matchesPattern("PAT-\\d{6}")))
                .andExpect(jsonPath("$.bloodGroup").value("A-"));
        try (Stream<Path> files = Files.list(Path.of("target/test-mail-outbox"))) {
            assertThat(files.anyMatch(f -> {
                try {
                    String s = Files.readString(f);
                    return s.contains("To: " + email) && s.contains("activate your patient account") && s.contains("reset-password?token=");
                } catch (Exception e) {
                    return false;
                }
            })).isTrue();
        }
        mockMvc.perform(post("/api/reception/patients").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Dup\",\"email\":\"%s\",\"phone\":\"+91 98000 22222\",\"dateOfBirth\":\"1970-07-07\"}".formatted(email)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/reception/patients").param("search", email).session(desk))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyFrontDeskCanUseReceptionEndpoints() throws Exception {
        MockHttpSession pat = login(patient().getUser().getEmail());
        mockMvc.perform(get("/api/reception/patients").session(pat)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reception/queue").session(login(doctor.getUser().getEmail()))).andExpect(status().isForbidden());
        Appointment a = appointment(patient(), today, LocalTime.of(10, 0));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", a.getId()).with(csrf()).session(pat)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reception/dashboard").session(loginAs(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void receptionUpdatesContactDetailsOnly() throws Exception {
        Patient p = patient();
        mockMvc.perform(patch("/api/reception/patients/{id}/contact", p.getId()).with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+91 91111 00000\",\"address\":\"New street\",\"fullName\":\"Hacked Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+91 91111 00000"))
                .andExpect(jsonPath("$.address").value("New street"))
                .andExpect(jsonPath("$.fullName").value("Quinn Patient"));
    }

    // ------------------------------------------------------------- booking

    @Test
    void frontDeskBooksWalkInAndCannotSeeTheVisitReason() throws Exception {
        for (int d = 1; d <= 7; d++) {
            DoctorAvailability av = new DoctorAvailability(doctor.getId(), d);
            av.update(LocalTime.of(9, 0), LocalTime.of(17, 0), null, null, true);
            availabilityRepository.save(av);
        }
        Patient p = patient();
        String json = mockMvc.perform(post("/api/appointments").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":%d,\"patientId\":%d,\"date\":\"%s\",\"startTime\":\"10:00\",\"reason\":\"Severe migraine\",\"walkIn\":true}"
                                .formatted(doctor.getId(), p.getId(), today.plusDays(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WALK_IN"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        mockMvc.perform(get("/api/appointments/{id}", id).session(login(doctor.getUser().getEmail())))
                .andExpect(jsonPath("$.reason").value("Severe migraine"));
        mockMvc.perform(get("/api/reception/patients/{pid}/appointments", p.getId()).session(desk))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].reason").doesNotExist());
    }

    // ------------------------------------------------------------- check-in & queue

    @Test
    void checkInIssuesSequentialTokensOnlyForTodaysScheduledAppointments() throws Exception {
        Appointment a1 = appointment(patient(), today, LocalTime.of(9, 0));
        Appointment a2 = appointment(patient(), today, LocalTime.of(9, 15));
        Appointment tomorrow = appointment(patient(), today.plusDays(1), LocalTime.of(9, 0));

        mockMvc.perform(patch("/api/appointments/{id}/check-in", a2.getId()).with(csrf()).session(desk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.queueToken").value(1))
                .andExpect(jsonPath("$.queueStatus").value("WAITING"));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", a1.getId()).with(csrf()).session(desk))
                .andExpect(jsonPath("$.queueToken").value(2));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", a1.getId()).with(csrf()).session(desk))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", tomorrow.getId()).with(csrf()).session(desk))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_TODAY"));

        mockMvc.perform(get("/api/reception/queue").param("doctorId", doctor.getId().toString()).session(desk))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tokenNumber").value(1))
                .andExpect(jsonPath("$[0].appointmentId").value(a2.getId()));
        mockMvc.perform(get("/api/doctor/queue").session(login(doctor.getUser().getEmail())))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void queueFollowsTheConsultationAndCancellation() throws Exception {
        Appointment a = appointment(patient(), today, LocalTime.of(23, 0));
        Appointment b = appointment(patient(), today, LocalTime.of(23, 15));
        Appointment walkInNoCheckIn = appointment(patient(), today, LocalTime.of(23, 30));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", a.getId()).with(csrf()).session(desk));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", b.getId()).with(csrf()).session(desk));

        MockHttpSession doc = login(doctor.getUser().getEmail());
        String json = mockMvc.perform(post("/api/consultations").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\":" + a.getId() + "}")).andReturn().getResponse().getContentAsString();
        long cid = Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        assertThat(queueStatus(a)).isEqualTo(QueueStatus.IN_CONSULTATION);

        mockMvc.perform(post("/api/consultations/{id}/finalize", cid).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosis\":\"Fictional\",\"version\":0}")).andExpect(status().isOk());
        assertThat(queueStatus(a)).isEqualTo(QueueStatus.COMPLETED);

        mockMvc.perform(patch("/api/appointments/{id}/cancel", b.getId()).with(csrf()).session(desk)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Left the hospital\"}")).andExpect(status().isOk());
        assertThat(queueStatus(b)).isEqualTo(QueueStatus.SKIPPED);

        // a doctor starting a consultation for a patient reception never checked in still gets a token
        mockMvc.perform(post("/api/consultations").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\":" + walkInNoCheckIn.getId() + "}")).andExpect(status().isOk());
        assertThat(queueRepository.findByAppointmentId(walkInNoCheckIn.getId()).orElseThrow().getTokenNumber()).isEqualTo(3);
        assertThat(queueStatus(walkInNoCheckIn)).isEqualTo(QueueStatus.IN_CONSULTATION);
    }

    @Test
    void skipRequeueAndNoShowKeepQueueAndAppointmentConsistent() throws Exception {
        Appointment a = appointment(patient(), today, LocalTime.MIDNIGHT);
        String json = mockMvc.perform(patch("/api/appointments/{id}/check-in", a.getId()).with(csrf()).session(desk))
                .andReturn().getResponse().getContentAsString();
        long entryId = queueRepository.findByAppointmentId(a.getId()).orElseThrow().getId();
        assertThat(json).contains("\"queueToken\":1");

        mockMvc.perform(patch("/api/reception/queue/{id}/skip", entryId).with(csrf()).session(desk))
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.appointmentStatus").value("CHECKED_IN"));
        mockMvc.perform(patch("/api/reception/queue/{id}/requeue", entryId).with(csrf()).session(desk))
                .andExpect(jsonPath("$.status").value("WAITING"));
        mockMvc.perform(patch("/api/appointments/{id}/no-show", a.getId()).with(csrf()).session(desk))
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.queueStatus").value("SKIPPED"));
        mockMvc.perform(patch("/api/reception/queue/{id}/requeue", entryId).with(csrf()).session(desk))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void simultaneousCheckInsNeverShareAToken() throws Exception {
        int n = 6;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(appointment(patient(), today, LocalTime.of(12, 0).plusMinutes(15L * i)).getId());
        }
        User deskUser = createUser(Role.RECEPTIONIST, "Desk Thread");
        AppUserPrincipal actor = new AppUserPrincipal(deskUser.getId(), deskUser.getEmail(), "Desk", Role.RECEPTIONIST, Instant.now());
        Set<Integer> tokens = ConcurrentHashMap.newKeySet();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Long id : ids) {
                futures.add(pool.submit(() -> {
                    go.await();
                    tokens.add(queueService.checkIn(actor, id).getTokenNumber());
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertThat(tokens).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    void dashboardCountsTodaysActivity() throws Exception {
        Appointment a = appointment(patient(), today, LocalTime.of(8, 0));
        mockMvc.perform(patch("/api/appointments/{id}/check-in", a.getId()).with(csrf()).session(desk));
        mockMvc.perform(get("/api/reception/dashboard").session(desk))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].waiting".formatted(doctor.getId())).value(1));
    }

    private QueueStatus queueStatus(Appointment a) {
        return queueRepository.findByAppointmentId(a.getId()).orElseThrow().getStatus();
    }
}
