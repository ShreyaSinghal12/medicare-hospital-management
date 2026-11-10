package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.medicare.entity.Appointment;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentStatus;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.Role;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.AuditLogRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.service.HospitalSettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Phase 5: consultations, clinical access control, amendments, allergies and medical history. */
class ConsultationIntegrationTest extends IntegrationTestBase {

    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired HospitalSettingsService settingsService;

    Doctor doctor;
    Patient patient;
    LocalDate today;

    @BeforeEach
    void setUp() {
        Department dept = createDepartment(unique("Clin"));
        doctor = createDoctor("Dr Consult " + unique("a").replaceAll("[^a-z]", "q"), dept, "Physician", "400");
        patient = createPatient();
        today = LocalDate.now(ZoneId.of(settingsService.getSettings().getTimeZone()));
    }

    Patient createPatient() {
        User u = userRepository.save(new User(uniqueEmail("cp"), passwordHash(), "Clara Patient", "+91 90000 44444", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("C").toUpperCase().substring(0, 10), LocalDate.of(1985, 3, 3)));
    }

    /** Appointments are inserted directly so tests can use "today" regardless of the time of day. */
    Appointment appointment(Doctor d, Patient p, LocalDate date, String time) {
        LocalTime start = LocalTime.parse(time);
        return appointmentRepository.save(new Appointment(unique("APT"), p, d, null, date, start, start.plusMinutes(15),
                AppointmentType.NEW, "Headache for three days", p.getUser().getId()));
    }

    long start(MockHttpSession doc, Appointment a) throws Exception {
        String body = mockMvc.perform(post("/api/consultations").with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"appointmentId\":" + a.getId() + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    static String content(String diagnosis, long version, String followUp) {
        return """
                {"symptoms":"Headache, mild fever","observations":"Temp 37.9 C (fictional)","diagnosis":%s,
                 "treatmentPlan":"Rest and fluids","notes":"Internal note","followUpDate":%s,
                 "followUpNotes":"Review in a week","version":%d}
                """.formatted(diagnosis == null ? "null" : "\"" + diagnosis + "\"",
                followUp == null ? "null" : "\"" + followUp + "\"", version);
    }

    // ------------------------------------------------------------- lifecycle

    @Test
    void doctorStartsDraftsAndFinalizesAndTheAppointmentIsCompleted() throws Exception {
        Appointment a = appointment(doctor, patient, today, "23:30");
        MockHttpSession doc = login(doctor.getUser().getEmail());
        long id = start(doc, a);
        assertThat(appointmentRepository.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.IN_PROGRESS);
        mockMvc.perform(get("/api/appointments/{id}/history", a.getId()).session(doc))
                .andExpect(jsonPath("$[*].action", hasItem("CHECKED_IN")))
                .andExpect(jsonPath("$[*].action", hasItem("CONSULTATION_STARTED")));
        assertThat(start(doc, a)).isEqualTo(id); // starting again re-opens the same consultation

        mockMvc.perform(patch("/api/consultations/{id}", id).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content(content(null, 0, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.canEdit").value(true));

        mockMvc.perform(post("/api/consultations/{id}/finalize", id).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content(content(null, 1, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DIAGNOSIS_REQUIRED"));

        mockMvc.perform(post("/api/consultations/{id}/finalize", id).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content(content("Viral fever (fictional)", 1, today.plusDays(7).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.appointmentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canAmend").value(true));

        mockMvc.perform(patch("/api/consultations/{id}", id).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content(content("Changed", 2, null)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONSULTATION_FINALIZED"));
    }

    @Test
    void staleVersionIsRejectedSoConcurrentEditsAreNotLost() throws Exception {
        Appointment a = appointment(doctor, patient, today, "23:00");
        MockHttpSession doc = login(doctor.getUser().getEmail());
        long id = start(doc, a);
        mockMvc.perform(patch("/api/consultations/{id}", id).with(csrf()).session(doc)
                .contentType(MediaType.APPLICATION_JSON).content(content("First", 0, null))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/consultations/{id}", id).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content(content("Second from old tab", 0, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void consultationsCanOnlyStartOnTheAppointmentDayAndFollowUpMustBeInFuture() throws Exception {
        MockHttpSession doc = login(doctor.getUser().getEmail());
        Appointment future = appointment(doctor, patient, today.plusDays(2), "10:00");
        mockMvc.perform(post("/api/consultations").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":" + future.getId() + "}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_TODAY"));

        long id = start(doc, appointment(doctor, patient, today, "22:30"));
        mockMvc.perform(patch("/api/consultations/{id}", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(content("x", 0, today.minusDays(1).toString())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_FOLLOW_UP"));
    }

    // ------------------------------------------------------------- access control

    @Test
    void onlyTheTreatingDoctorWritesAndNonClinicalStaffCannotRead() throws Exception {
        Appointment a = appointment(doctor, patient, today, "21:00");
        long id = start(login(doctor.getUser().getEmail()), a);

        Doctor other = createDoctor("Dr Other " + unique("b").replaceAll("[^a-z]", "q"), doctor.getDepartment(), "Physician", "100");
        MockHttpSession otherSession = login(other.getUser().getEmail());
        mockMvc.perform(post("/api/consultations").with(csrf()).session(otherSession).contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\":" + a.getId() + "}")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/consultations/{id}", id).session(otherSession)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/consultations/{id}", id).with(csrf()).session(otherSession)
                .contentType(MediaType.APPLICATION_JSON).content(content("Hack", 0, null))).andExpect(status().isForbidden());

        for (Role role : new Role[]{Role.RECEPTIONIST, Role.ADMIN}) {
            MockHttpSession s = loginAs(role);
            mockMvc.perform(get("/api/consultations/{id}", id).session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/doctor/patients/{pid}/record", patient.getId()).session(s)).andExpect(status().isForbidden());
        }
    }

    @Test
    void recordAccessRequiresTreatmentRelationshipAndIsAudited() throws Exception {
        appointment(doctor, patient, today.plusDays(5), "11:00");
        MockHttpSession doc = login(doctor.getUser().getEmail());
        mockMvc.perform(get("/api/doctor/patients/{pid}/record", patient.getId()).session(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.patientCode").value(patient.getPatientCode()))
                .andExpect(jsonPath("$.visits[0].status").value("SCHEDULED"));
        assertThat(auditLogRepository.countByActionAndEntityTypeAndEntityId("PATIENT_RECORD_VIEWED", "Patient",
                patient.getId().toString())).isEqualTo(1);

        Doctor stranger = createDoctor("Dr Stranger " + unique("c").replaceAll("[^a-z]", "q"), doctor.getDepartment(), "Physician", "100");
        mockMvc.perform(get("/api/doctor/patients/{pid}/record", patient.getId()).session(login(stranger.getUser().getEmail())))
                .andExpect(status().isForbidden());
        assertThat(auditLogRepository.countByActionAndEntityTypeAndEntityId("CLINICAL_ACCESS_DENIED", "Patient",
                patient.getId().toString())).isGreaterThanOrEqualTo(1);

        mockMvc.perform(get("/api/doctor/patients").param("search", patient.getPatientCode()).session(doc))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void cancelledAppointmentsDoNotCreateATreatmentRelationship() throws Exception {
        Appointment a = appointment(doctor, patient, today.plusDays(3), "11:00");
        a.cancel(patient.getUser().getId(), "test");
        appointmentRepository.save(a);
        mockMvc.perform(get("/api/doctor/patients/{pid}/record", patient.getId()).session(login(doctor.getUser().getEmail())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- patient view & amendments

    @Test
    void patientSeesOnlyFinalizedNotesWithoutClinicianFieldsAndAmendmentsKeepHistory() throws Exception {
        Appointment a = appointment(doctor, patient, today, "20:00");
        MockHttpSession doc = login(doctor.getUser().getEmail());
        long id = start(doc, a);
        MockHttpSession pat = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/consultations/{id}", id).session(pat)).andExpect(status().isNotFound()); // draft is private

        mockMvc.perform(post("/api/consultations/{id}/finalize", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                .content(content("Migraine (fictional)", 0, today.plusDays(10).toString()))).andExpect(status().isOk());

        mockMvc.perform(get("/api/consultations/{id}", id).session(pat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Migraine (fictional)"))
                .andExpect(jsonPath("$.observations").doesNotExist())
                .andExpect(jsonPath("$.notes").doesNotExist());
        mockMvc.perform(get("/api/consultations/{id}", id).session(login(createPatient().getUser().getEmail())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/consultations/{id}/amendments", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":" + content("Tension headache (fictional)", 1, today.plusDays(10).toString()) + ",\"reason\":\"Revised after review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amendmentCount").value(1))
                .andExpect(jsonPath("$.diagnosis").value("Tension headache (fictional)"));
        mockMvc.perform(get("/api/consultations/{id}/amendments", id).session(doc))
                .andExpect(jsonPath("$[0].reason").value("Revised after review"))
                .andExpect(jsonPath("$[0].previousValues.diagnosis").value("Migraine (fictional)"));
        mockMvc.perform(post("/api/consultations/{id}/amendments", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":" + content("Tension headache (fictional)", 2, today.plusDays(10).toString()) + ",\"reason\":\"No change at all\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_CHANGES"));

        mockMvc.perform(get("/api/patients/me/medical-history").session(pat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[0].diagnosis").value("Tension headache (fictional)"))
                .andExpect(jsonPath("$.timeline[0].amended").value(true))
                .andExpect(jsonPath("$.timeline[0].followUpDate").value(today.plusDays(10).toString()));
    }

    @Test
    void doctorRecordsAllergiesAndConditionsThatThePatientCanSee() throws Exception {
        appointment(doctor, patient, today.plusDays(1), "09:00");
        MockHttpSession doc = login(doctor.getUser().getEmail());
        String body = mockMvc.perform(post("/api/doctor/patients/{pid}/allergies", patient.getId()).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergen\":\"Penicillin\",\"reaction\":\"Rash\",\"severity\":\"SEVERE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long allergyId = Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/doctor/patients/{pid}/allergies", patient.getId()).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergen\":\"penicillin\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/doctor/patients/{pid}/conditions", patient.getId()).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"conditionName\":\"Hypertension (fictional)\",\"status\":\"CHRONIC\"}"))
                .andExpect(status().isCreated());

        MockHttpSession pat = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/patients/me/medical-history").session(pat))
                .andExpect(jsonPath("$.allergies[0].allergen").value("Penicillin"))
                .andExpect(jsonPath("$.conditions[0].status").value("CHRONIC"));

        mockMvc.perform(patch("/api/doctor/patients/{pid}/allergies/{aid}", patient.getId(), allergyId).with(csrf()).session(doc)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/patients/me/medical-history").session(pat))
                .andExpect(jsonPath("$.allergies").isEmpty());

        // patients cannot write clinical data
        mockMvc.perform(post("/api/doctor/patients/{pid}/allergies", patient.getId()).with(csrf()).session(pat)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"allergen\":\"Self-entered\"}"))
                .andExpect(status().isForbidden());
    }
}
