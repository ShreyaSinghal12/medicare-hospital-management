package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.medicare.entity.Appointment;
import com.medicare.entity.Consultation;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.LabTest;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.Role;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.ConsultationRepository;
import com.medicare.repository.LabTestRepository;
import com.medicare.repository.NotificationRepository;
import com.medicare.repository.PatientRepository;
import com.medicare.service.HospitalSettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Phase 8: lab catalog, orders, results, secure PDF upload/download, release to patients and access rules. */
class LabIntegrationTest extends IntegrationTestBase {

    static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n"
            .getBytes(StandardCharsets.ISO_8859_1);

    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired LabTestRepository labTestRepository;
    @Autowired HospitalSettingsService settingsService;
    @Autowired JdbcTemplate jdbc;
    @Value("${medicare.upload-dir}") String uploadDir;

    Department dept;
    Doctor doctor;
    Patient patient;
    Consultation consultation;
    MockHttpSession doc;
    LabTest t1;
    LabTest t2;

    @BeforeEach
    void setUp() throws Exception {
        dept = createDepartment(unique("Lab"));
        doctor = createDoctor("Dr Lab " + letters(), dept, "Physician", "400");
        patient = newPatient("Lata Labpatient");
        consultation = consultationFor(doctor, patient, LocalTime.of(22, 0));
        doc = login(doctor.getUser().getEmail());
        t1 = newTest("Glucose");
        t2 = newTest("Thyroid");
    }

    // ------------------------------------------------------------------ helpers

    static String letters() {
        return unique("x").replaceAll("[^a-z]", "q");
    }

    Patient newPatient(String name) {
        User u = userRepository.save(new User(uniqueEmail("lp"), passwordHash(), name, "+91 90000 44444", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("L").toUpperCase().substring(0, 10), LocalDate.of(1985, 3, 3)));
    }

    Consultation consultationFor(Doctor d, Patient p, LocalTime start) {
        LocalDate today = LocalDate.now(ZoneId.of(settingsService.getSettings().getTimeZone()));
        Appointment a = appointmentRepository.save(new Appointment(unique("APT"), p, d, null, today, start,
                start.plusMinutes(15), AppointmentType.NEW, "Check-up", p.getUser().getId()));
        return consultationRepository.save(new Consultation(a));
    }

    LabTest newTest(String prefix) {
        String code = "T" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000_000));
        LabTest t = new LabTest(code);
        t.update(code, prefix + " " + unique("t"), "Fictional test", "Blood", "mg/dL", "70–100",
                new java.math.BigDecimal("250.00"), 12);
        return labTestRepository.save(t);
    }

    long order(Long... testIds) throws Exception {
        String ids = String.join(",", Arrays.stream(testIds).map(String::valueOf).toList());
        String json = mockMvc.perform(post("/api/lab-orders").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[" + ids
                                + "],\"priority\":\"URGENT\",\"clinicalNotes\":\"Rule out thyroid issue (fictional)\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    ResultActions setStatus(long id, String s, String reason) throws Exception {
        return mockMvc.perform(patch("/api/lab-orders/{id}/status", id).with(csrf()).session(doc)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + s + "\"" + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}"));
    }

    ResultActions upload(long id, String name, byte[] bytes, MockHttpSession session) throws Exception {
        return mockMvc.perform(multipart("/api/lab-orders/{id}/report", id)
                .file(new MockMultipartFile("file", name, "application/pdf", bytes)).with(csrf()).session(session));
    }

    long itemId(long orderId, int index) throws Exception {
        String json = mockMvc.perform(get("/api/lab-orders/{id}", orderId).session(doc)).andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll("(?s).*?\"items\":\\[(.*)", "$1").split("\"id\":")[index + 1].replaceAll("(?s)^(\\d+).*", "$1"));
    }

    List<String> storedFiles() throws IOException {
        Path dir = Path.of(uploadDir, "lab-reports");
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        }
    }

    // ------------------------------------------------------------------ catalog

    @Test
    void adminManagesCatalogAndDoctorsSeeOnlyActiveTests() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        String code = "Z" + Math.abs(System.nanoTime() % 100_000_000L);
        String body = """
                {"code":"%s","name":"Vitamin D %s","description":"Fictional","sampleType":"Blood","unit":"ng/mL",
                 "referenceRange":"30–100","price":900.50,"turnaroundHours":48}""".formatted(code, letters());
        String json = mockMvc.perform(post("/api/admin/lab-tests").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.price").value(900.50))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(json.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/admin/lab-tests").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("Vitamin D", "Other")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAB_TEST_CODE_EXISTS"));
        mockMvc.perform(post("/api/admin/lab-tests").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"price\":900.50", "\"price\":-1")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/lab-tests/{id}", id).with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("900.50", "950.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(950.0));

        mockMvc.perform(get("/api/lab-tests").session(doc)).andExpect(jsonPath("$[*].code", hasItem(code)));
        mockMvc.perform(patch("/api/admin/lab-tests/{id}/status", id).with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/lab-tests").session(doc)).andExpect(jsonPath("$[*].code", not(hasItem(code))));
        mockMvc.perform(post("/api/lab-orders").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[" + id + "]}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("LAB_TEST_INACTIVE"));

        for (MockHttpSession s : new MockHttpSession[]{doc, loginAs(Role.RECEPTIONIST)}) {
            mockMvc.perform(post("/api/admin/lab-tests").with(csrf()).session(s).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isForbidden());
        }
    }

    // ------------------------------------------------------------------ full workflow

    @Test
    void doctorOrdersRecordsUploadsAndFinalizesThenPatientDownloads() throws Exception {
        long id = order(t1.getId(), t2.getId());
        mockMvc.perform(get("/api/lab-orders/{id}", id).session(doc))
                .andExpect(jsonPath("$.orderNo", matchesPattern("LAB-\\d{8}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].price").value(250.0))
                .andExpect(jsonPath("$.items[0].referenceRange").value("70–100"))
                .andExpect(jsonPath("$.canManage").value(true));

        upload(id, "report.pdf", PDF, doc).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAMPLE_NOT_COLLECTED"));
        setStatus(id, "PROCESSING", null).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        setStatus(id, "SAMPLE_COLLECTED", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCollectedAt").isNotEmpty());

        long item = itemId(id, 0);
        mockMvc.perform(put("/api/lab-orders/{id}/results", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":" + item + ",\"resultValue\":\"92\",\"resultComment\":\"Fasting sample\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].resultValue").value("92"));
        mockMvc.perform(put("/api/lab-orders/{id}/results", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"items\":[{\"itemId\":" + item + ",\"resultValue\":\"1\"}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        upload(id, "C:\\fakepath\\../../Glucose report.pdf", PDF, doc).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.report.fileName").value("Glucose report.pdf"))
                .andExpect(jsonPath("$.report.finalized").value(false));
        assertThat(storedFiles()).allMatch(f -> f.matches("[0-9a-f]{32}\\.pdf"));

        // patient: order visible, but no results and no report before finalization
        MockHttpSession pat = login(patient.getUser().getEmail());
        mockMvc.perform(get("/api/patients/me/lab-orders").session(pat))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].hasReport").value(false));
        mockMvc.perform(get("/api/lab-orders/{id}", id).session(pat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalNotes").doesNotExist())
                .andExpect(jsonPath("$.items[0].resultValue").doesNotExist())
                .andExpect(jsonPath("$.report").doesNotExist());
        mockMvc.perform(get("/api/lab-orders/{id}/report", id).session(pat)).andExpect(status().isNotFound());

        long before = notificationRepository.countByUserIdAndReadFalse(patient.getUser().getId());
        mockMvc.perform(post("/api/lab-orders/{id}/finalize", id).with(csrf()).session(doc))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.canManage").value(false));
        assertThat(notificationRepository.countByUserIdAndReadFalse(patient.getUser().getId())).isEqualTo(before + 1);

        mockMvc.perform(get("/api/lab-orders/{id}", id).session(pat))
                .andExpect(jsonPath("$.items[0].resultValue").value("92"))
                .andExpect(jsonPath("$.report.finalized").value(true))
                .andExpect(jsonPath("$.clinicalNotes").doesNotExist());
        mockMvc.perform(get("/api/lab-orders/{id}/report", id).session(pat))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", matchesPattern("attachment; filename=\"LAB-\\d{8}-\\d{6}\\.pdf\"")))
                .andExpect(content().bytes(PDF));
        Integer downloads = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LAB_REPORT_DOWNLOADED' AND entity_id = ?",
                Integer.class, String.valueOf(id));
        assertThat(downloads).isEqualTo(1);

        // finalized orders are immutable
        mockMvc.perform(put("/api/lab-orders/{id}/results", id).with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":" + item + ",\"resultValue\":\"1\"}]}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ORDER_CLOSED"));
        upload(id, "new.pdf", PDF, doc).andExpect(jsonPath("$.code").value("ORDER_CLOSED"));
        mockMvc.perform(delete("/api/lab-orders/{id}/report", id).with(csrf()).session(doc))
                .andExpect(jsonPath("$.code").value("ORDER_CLOSED"));
    }

    @Test
    void finalizeNeedsAReportAndReplacingAReportRemovesTheOldFile() throws Exception {
        long id = order(t1.getId());
        setStatus(id, "SAMPLE_COLLECTED", null).andExpect(status().isOk());
        mockMvc.perform(post("/api/lab-orders/{id}/finalize", id).with(csrf()).session(doc))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("REPORT_REQUIRED"));

        upload(id, "first.pdf", PDF, doc).andExpect(status().isOk());
        List<String> afterFirst = storedFiles();
        upload(id, "second.pdf", PDF, doc).andExpect(status().isOk()).andExpect(jsonPath("$.report.fileName").value("second.pdf"));
        List<String> afterSecond = storedFiles();
        assertThat(afterSecond).hasSameSizeAs(afterFirst);
        assertThat(afterSecond).isNotEqualTo(afterFirst);

        mockMvc.perform(delete("/api/lab-orders/{id}/report", id).with(csrf()).session(doc))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report").doesNotExist());
        assertThat(storedFiles()).hasSize(afterSecond.size() - 1);
    }

    // ------------------------------------------------------------------ upload safety

    @Test
    void unsafeOrInvalidUploadsAreRejectedAndNothingIsStored() throws Exception {
        long id = order(t1.getId());
        setStatus(id, "SAMPLE_COLLECTED", null).andExpect(status().isOk());
        int filesBefore = storedFiles().size();

        upload(id, "report.exe", PDF, doc).andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        upload(id, "fake.pdf", "MZ this is not a pdf".getBytes(StandardCharsets.US_ASCII), doc)
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        upload(id, "truncated.pdf", "%PDF-1.4\n1 0 obj".getBytes(StandardCharsets.US_ASCII), doc)
                .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
        upload(id, "script.pdf", ("%PDF-1.4\n1 0 obj << /OpenAction << /S /JavaScript /JS (app.alert(1)) >> >> endobj\n%%EOF")
                .getBytes(StandardCharsets.US_ASCII), doc)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("UNSAFE_FILE"));
        byte[] big = new byte[5 * 1024 * 1024 + 10];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(id, "big.pdf", big, doc).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
        mockMvc.perform(multipart("/api/lab-orders/{id}/report", id).with(csrf()).session(doc))
                .andExpect(jsonPath("$.code").value("FILE_REQUIRED"));

        assertThat(storedFiles()).hasSize(filesBefore);
        mockMvc.perform(get("/api/lab-orders/{id}", id).session(doc)).andExpect(jsonPath("$.status").value("SAMPLE_COLLECTED"));
    }

    // ------------------------------------------------------------------ access rules

    @Test
    void onlyThePatientAndTreatingDoctorsCanReadAndOnlyTheOrderingDoctorCanChange() throws Exception {
        long id = order(t1.getId());
        setStatus(id, "SAMPLE_COLLECTED", null);
        upload(id, "r.pdf", PDF, doc);
        mockMvc.perform(post("/api/lab-orders/{id}/finalize", id).with(csrf()).session(doc)).andExpect(status().isOk());

        // receptionist and admin: refused and audited
        for (Role role : new Role[]{Role.RECEPTIONIST, Role.ADMIN}) {
            MockHttpSession s = loginAs(role);
            mockMvc.perform(get("/api/lab-orders/{id}", id).session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/lab-orders/{id}/report", id).session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/doctor/lab-orders").session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/patients/me/lab-orders").session(s)).andExpect(status().isForbidden());
        }
        Integer denied = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'LAB_ACCESS_DENIED' AND entity_id = ?",
                Integer.class, String.valueOf(id));
        assertThat(denied).isEqualTo(4);

        // another patient: not found
        MockHttpSession otherPatient = login(newPatient("Omar Otherpatient").getUser().getEmail());
        mockMvc.perform(get("/api/lab-orders/{id}", id).session(otherPatient)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lab-orders/{id}/report", id).session(otherPatient)).andExpect(status().isNotFound());

        // a doctor without a treatment relationship: refused
        Doctor stranger = createDoctor("Dr Stranger " + letters(), dept, "Physician", "300");
        MockHttpSession strangerSession = login(stranger.getUser().getEmail());
        mockMvc.perform(get("/api/lab-orders/{id}", id).session(strangerSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/doctor/patients/{pid}/lab-orders", patient.getId()).session(strangerSession))
                .andExpect(status().isForbidden());

        // once that doctor also treats the patient: can read completed results, but cannot change the order
        consultationFor(stranger, patient, LocalTime.of(22, 30));
        mockMvc.perform(get("/api/lab-orders/{id}", id).session(strangerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.clinicalNotes").value("Rule out thyroid issue (fictional)"));
        mockMvc.perform(get("/api/doctor/patients/{pid}/lab-orders", patient.getId()).session(strangerSession))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(get("/api/lab-orders/{id}/report", id).session(strangerSession)).andExpect(status().isOk());

        long open = order(t2.getId());
        mockMvc.perform(patch("/api/lab-orders/{id}/status", open).with(csrf()).session(strangerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SAMPLE_COLLECTED\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/lab-orders").with(csrf()).session(strangerSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[" + t1.getId() + "]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/lab-orders").with(csrf()).session(login(patient.getUser().getEmail()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[" + t1.getId() + "]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void orderValidationAndCancellation() throws Exception {
        mockMvc.perform(post("/api/lab-orders").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/lab-orders").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[" + t1.getId() + "," + t1.getId() + "]}"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_TEST"));
        mockMvc.perform(post("/api/lab-orders").with(csrf()).session(doc).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":" + consultation.getId() + ",\"testIds\":[999999999]}"))
                .andExpect(status().isNotFound());

        long id = order(t1.getId());
        setStatus(id, "CANCELLED", null).andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        setStatus(id, "CANCELLED", "Patient declined (fictional)").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(0));
        setStatus(id, "SAMPLE_COLLECTED", null).andExpect(jsonPath("$.code").value("ORDER_CLOSED"));

        mockMvc.perform(get("/api/doctor/lab-orders").param("status", "CANCELLED").session(doc))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(get("/api/consultations/{cid}/lab-orders", consultation.getId()).session(doc))
                .andExpect(jsonPath("$[0].id").value(id));
    }
}
