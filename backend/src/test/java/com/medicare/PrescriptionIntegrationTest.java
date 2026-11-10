package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import com.medicare.entity.Appointment;
import com.medicare.entity.Consultation;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.Role;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.NotificationRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.service.HospitalSettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

/** Phase 6: prescription drafts, finalization, revisions, access rules, search and PDF. */
class PrescriptionIntegrationTest extends IntegrationTestBase {

    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired HospitalSettingsService settingsService;

    Doctor doctor;
    Patient patient;
    Consultation consultation;
    MockHttpSession doc;

    @BeforeEach
    void setUp() throws Exception {
        Department dept = createDepartment(unique("Rx"));
        doctor = createDoctor("Dr Rx " + unique("a").replaceAll("[^a-z]", "q"), dept, "Physician", "400");
        User u = userRepository.save(new User(uniqueEmail("rxp"), passwordHash(), "Rita Rxpatient", "+91 90000 55555", Role.PATIENT));
        patient = patientRepository.save(new Patient(u, unique("R").toUpperCase().substring(0, 10), LocalDate.of(1980, 2, 2)));
        LocalDate today = LocalDate.now(ZoneId.of(settingsService.getSettings().getTimeZone()));
        Appointment a = appointmentRepository.save(new Appointment(unique("APT"), patient, doctor, null, today,
                LocalTime.of(23, 0), LocalTime.of(23, 15), AppointmentType.NEW, "Fever", u.getId()));
        consultation = consultationRepository.save(new Consultation(a));
        doc = login(doctor.getUser().getEmail());
    }

    static String body(Long consultationId, Long version, String... medicines) {
        StringBuilder items = new StringBuilder();
        for (String m : medicines) {
            items.append(items.isEmpty() ? "" : ",").append("""
                    {"medicineName":"%s","strength":"500 mg","dosage":"1 tablet","frequency":"Twice daily",
                     "duration":"5 days","instructions":"After food","quantity":10}""".formatted(m));
        }
        return "{" + (consultationId == null ? "" : "\"consultationId\":" + consultationId + ",")
                + (version == null ? "" : "\"version\":" + version + ",")
                + "\"generalInstructions\":\"Drink plenty of water\",\"items\":[" + items + "]}";
    }

    long createDraft(String... medicines) throws Exception {
        String json = mockMvc.perform(post("/api/prescriptions").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(body(consultation.getId(), null, medicines)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    void finalizeRx(long id) throws Exception {
        mockMvc.perform(post("/api/prescriptions/{id}/finalize", id).with(csrf()).session(doc)).andExpect(status().isOk());
    }

    @Test
    void doctorCreatesEditsAndFinalizesAndThePatientIsNotified() throws Exception {
        mockMvc.perform(post("/api/prescriptions").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(body(consultation.getId(), null, "Paracetamol (fictional)")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prescriptionNo", matchesPattern("RX-\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.items[0].strength").value("500 mg"))
                .andExpect(jsonPath("$.canEdit").value(true));
        long id = consultationRx();

        mockMvc.perform(put("/api/prescriptions/{id}", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, 0L, "Paracetamol (fictional)", "Cetirizine (fictional)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put("/api/prescriptions/{id}", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, 0L, "Stale tab")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // a second prescription for the same consultation is refused
        mockMvc.perform(post("/api/prescriptions").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(body(consultation.getId(), null, "Another")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_EXISTS"));

        long before = notificationRepository.countByUserIdAndReadFalse(patient.getUser().getId());
        finalizeRx(id);
        assertThat(notificationRepository.countByUserIdAndReadFalse(patient.getUser().getId())).isEqualTo(before + 1);

        mockMvc.perform(put("/api/prescriptions/{id}", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, null, "Changed after finalize")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRESCRIPTION_FINALIZED"));
        mockMvc.perform(delete("/api/prescriptions/{id}", id).with(csrf()).session(doc))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void validationRejectsEmptyOrIncompleteMedicineLines() throws Exception {
        mockMvc.perform(post("/api/prescriptions").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("items")));
        mockMvc.perform(post("/api/prescriptions").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"items\":[{\"medicineName\":\"X\",\"quantity\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("items[0].dosage")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("items[0].quantity")));
    }

    @Test
    void patientSeesOnlyIssuedVersionsAndOthersAreRefused() throws Exception {
        long id = createDraft("Amoxicillin (fictional)");
        MockHttpSession pat = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/prescriptions/{id}", id).session(pat)).andExpect(status().isNotFound()); // draft hidden
        mockMvc.perform(get("/api/patients/me/prescriptions").session(pat)).andExpect(jsonPath("$.totalElements").value(0));

        finalizeRx(id);
        mockMvc.perform(get("/api/prescriptions/{id}", id).session(pat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].medicineName").value("Amoxicillin (fictional)"))
                .andExpect(jsonPath("$.canEdit").value(false));

        MockHttpSession other = login(userRepository.save(new User(uniqueEmail("o"), passwordHash(), "Other P", "+91 90000 1", Role.PATIENT)).getEmail());
        mockMvc.perform(get("/api/prescriptions/{id}", id).session(other)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/prescriptions/{id}/pdf", id).session(other)).andExpect(status().isNotFound());
        for (Role role : new Role[]{Role.RECEPTIONIST, Role.ADMIN}) {
            mockMvc.perform(get("/api/prescriptions/{id}", id).session(loginAs(role))).andExpect(status().isForbidden());
        }
        Doctor stranger = createDoctor("Dr Stranger " + unique("s").replaceAll("[^a-z]", "q"), doctor.getDepartment(), "Physician", "1");
        mockMvc.perform(get("/api/prescriptions/{id}", id).session(login(stranger.getUser().getEmail()))).andExpect(status().isForbidden());
    }

    @Test
    void pdfIsGeneratedForFinalizedPrescriptions() throws Exception {
        long id = createDraft("Ibuprofen (fictional)");
        mockMvc.perform(get("/api/prescriptions/{id}/pdf", id).session(doc))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DRAFT_HAS_NO_PDF"));
        finalizeRx(id);
        byte[] pdf = mockMvc.perform(get("/api/prescriptions/{id}/pdf", id).session(login(patient.getUser().getEmail())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", matchesPattern("attachment; filename=\"RX-\\d{8}-\\d{6}-v1\\.pdf\"")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void revisionCreatesANewVersionAndSupersedesThePreviousOneOnFinalize() throws Exception {
        long v1 = createDraft("Drug A (fictional)");
        finalizeRx(v1);
        String json = mockMvc.perform(post("/api/prescriptions/{id}/revisions", v1).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Dose adjusted after review\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].medicineName").value("Drug A (fictional)"))
                .andReturn().getResponse().getContentAsString();
        long v2 = Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/prescriptions/{id}/revisions", v1).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Second revision\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVISION_IN_PROGRESS"));

        MockHttpSession pat = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/prescriptions/{id}", v1).session(pat)).andExpect(jsonPath("$.status").value("FINALIZED"));

        mockMvc.perform(put("/api/prescriptions/{id}", v2).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                .content(body(null, null, "Drug A (fictional) reduced"))).andExpect(status().isOk());
        finalizeRx(v2);

        mockMvc.perform(get("/api/prescriptions/{id}", v1).session(pat)).andExpect(jsonPath("$.status").value("SUPERSEDED"));
        mockMvc.perform(get("/api/prescriptions/{id}/versions", v2).session(pat))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"));
        mockMvc.perform(get("/api/patients/me/prescriptions").session(pat))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].versionNumber").value(2));
        mockMvc.perform(get("/api/patients/me/prescriptions").param("includeSuperseded", "true").session(pat))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/prescriptions/{id}/pdf", v1).session(pat)).andExpect(status().isOk()); // watermarked copy
        mockMvc.perform(post("/api/prescriptions/{id}/revisions", v1).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Revise an old one\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOT_CURRENT"));
    }

    @Test
    void patientSearchFiltersByMedicineDoctorAndDate() throws Exception {
        long id = createDraft("Zylomycin (fictional)");
        finalizeRx(id);
        MockHttpSession pat = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/patients/me/prescriptions").param("search", "zylo").session(pat))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].medicines[0]").value("Zylomycin (fictional)"));
        mockMvc.perform(get("/api/patients/me/prescriptions").param("search", "nothing-like-this").session(pat))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/patients/me/prescriptions").param("doctorId", "999999").session(pat))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/patients/me/prescriptions").param("from", "2000-01-01").param("to", "2000-01-31").session(pat))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/patients/me/prescriptions").param("from", "2030-01-02").param("to", "2030-01-01").session(pat))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void discardingARevisionDraftKeepsThePreviousVersionCurrent() throws Exception {
        long v1 = createDraft("Drug B (fictional)");
        finalizeRx(v1);
        String json = mockMvc.perform(post("/api/prescriptions/{id}/revisions", v1).with(csrf()).session(doc)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Considering change\"}"))
                .andReturn().getResponse().getContentAsString();
        long v2 = Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        mockMvc.perform(delete("/api/prescriptions/{id}", v2).with(csrf()).session(doc)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/prescriptions/{id}", v1).session(doc))
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.canRevise").value(true));
    }

    private long consultationRx() throws Exception {
        String json = mockMvc.perform(get("/api/consultations/{id}/prescriptions", consultation.getId()).session(doc))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }
}
