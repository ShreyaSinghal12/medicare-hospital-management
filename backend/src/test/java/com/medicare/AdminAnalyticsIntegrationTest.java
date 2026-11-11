package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;

import com.medicare.entity.Appointment;
import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.Patient;
import com.medicare.entity.User;
import com.medicare.enums.AppointmentType;
import com.medicare.enums.Role;
import com.medicare.repository.AppointmentRepository;
import com.medicare.repository.PatientRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

/** Phase 12: admin dashboard statistics, hospital settings, reports (preview/CSV/PDF) and the audit viewer. */
class AdminAnalyticsIntegrationTest extends IntegrationTestBase {

    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired JdbcTemplate jdbc;

    MockHttpSession admin;
    Doctor doctor;
    Department dept;
    /** A past week reserved for this test run, so counts are exact. */
    LocalDate d0;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAs(Role.ADMIN);
        dept = createDepartment(unique("Ana"));
        doctor = createDoctor("Dr Stats " + unique("s").replaceAll("[^a-z]", "q"), dept, "Physician", "300");
        d0 = LocalDate.of(1990 + java.util.concurrent.ThreadLocalRandom.current().nextInt(20), 3, 1)
                .plusDays(java.util.concurrent.ThreadLocalRandom.current().nextInt(200));
        while (jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE appointment_date BETWEEN ? AND ?", Integer.class, d0, d0.plusDays(4)) > 0) {
            d0 = d0.plusDays(17);
        }
    }

    Patient patient(String name) {
        User u = userRepository.save(new User(uniqueEmail("ap"), passwordHash(), name, "+91 90000 99999", Role.PATIENT));
        return patientRepository.save(new Patient(u, unique("A").toUpperCase().substring(0, 10), LocalDate.of(1990, 1, 1)));
    }

    Appointment appt(Patient p, LocalDate date, int hour, String status) {
        Appointment a = appointmentRepository.save(new Appointment(unique("APT"), p, doctor, null, date, LocalTime.of(hour, 0),
                LocalTime.of(hour, 15), AppointmentType.NEW, "Private reason (fictional)", p.getUser().getId()));
        jdbc.update("UPDATE appointments SET status = ? WHERE id = ?", status, a.getId());
        return a;
    }

    @Test
    void dashboardCountsAppointmentsPerStatusDayDepartmentAndDoctor() throws Exception {
        Patient p = patient("Stat Patient");
        appt(p, d0, 9, "COMPLETED");
        appt(p, d0, 10, "COMPLETED");
        appt(p, d0.plusDays(2), 9, "NO_SHOW");
        appt(p, d0.plusDays(4), 9, "CANCELLED");
        mockMvc.perform(get("/api/admin/dashboard").param("from", d0.toString()).param("to", d0.plusDays(4).toString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAppointments").value(4))
                .andExpect(jsonPath("$.completedAppointments").value(2))
                .andExpect(jsonPath("$.noShowAppointments").value(1))
                .andExpect(jsonPath("$.cancelledAppointments").value(1))
                .andExpect(jsonPath("$.appointmentsPerDay.length()").value(5))
                .andExpect(jsonPath("$.appointmentsPerDay[0].count").value(2))
                .andExpect(jsonPath("$.appointmentsPerDay[1].count").value(0))
                .andExpect(jsonPath("$.appointmentsPerDay[4].count").value(0))
                .andExpect(jsonPath("$.appointmentsByDepartment[0].name").value(dept.getName()))
                .andExpect(jsonPath("$.appointmentsByDepartment[0].count").value(3))
                .andExpect(jsonPath("$.doctorWorkload[0].total").value(3))
                .andExpect(jsonPath("$.doctorWorkload[0].completed").value(2))
                .andExpect(jsonPath("$.doctorWorkload[0].noShow").value(1))
                .andExpect(jsonPath("$.activeDoctors").isNumber());

        mockMvc.perform(get("/api/admin/dashboard").param("from", "2020-02-01").param("to", "2020-01-01").session(admin))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
        mockMvc.perform(get("/api/admin/dashboard").param("from", "2020-01-01").param("to", "2021-06-01").session(admin))
                .andExpect(jsonPath("$.code").value("RANGE_TOO_LONG"));
    }

    @Test
    void dashboardRevenueIncludesTodaysPayments() throws Exception {
        MockHttpSession desk = loginAs(Role.RECEPTIONIST);
        Patient p = patient("Revenue Patient");
        String inv = mockMvc.perform(post("/api/billing/invoices").with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":" + p.getId() + ",\"otherItems\":[{\"description\":\"Registration\",\"quantity\":1,\"unitPrice\":123.45}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(inv.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
        mockMvc.perform(post("/api/billing/invoices/{id}/payments", id).with(csrf()).session(desk).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"CASH\",\"amount\":123.45}")).andExpect(status().isOk());
        String json = mockMvc.perform(get("/api/admin/dashboard").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentsPerDay.length()").value(30))
                .andReturn().getResponse().getContentAsString();
        double lastDay = Double.parseDouble(json.replaceAll("(?s).*\"revenuePerDay\":\\[.*\\{\"date\":\"[^\"]+\",\"amount\":([0-9.\\-]+)\\}\\].*", "$1"));
        assertThat(lastDay).isGreaterThanOrEqualTo(123.45);
    }

    @Test
    void settingsAreValidatedAuditedAndTheTimeZoneIsNotEditable() throws Exception {
        String original = mockMvc.perform(get("/api/admin/settings").session(admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer window = jdbc.queryForObject("SELECT booking_window_days FROM hospital_settings WHERE id = 1", Integer.class);
        String zone = jdbc.queryForObject("SELECT time_zone FROM hospital_settings WHERE id = 1", String.class);
        String body = """
                {"hospitalName":"MediCare Community Hospital","address":"12 Example Health Avenue, Sample City","phone":"+91-00000-00000",
                 "email":"contact@medicare.example","defaultSlotMinutes":15,"bookingWindowDays":%d,"cancellationWindowHours":2,
                 "cancellationPolicy":"Fictional policy","invoicePrefix":"%s","taxRatePercent":%s,"emailRemindersEnabled":true,
                 "reminderHoursBefore":24,"expiryWarningDays":60,"timeZone":"UTC"}""";
        try {
            mockMvc.perform(put("/api/admin/settings").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                    .content(body.formatted(window, "inv", "0"))).andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/admin/settings").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                    .content(body.formatted(window, "INV", "101"))).andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/admin/settings").with(csrf()).session(admin).contentType(MediaType.APPLICATION_JSON)
                            .content(body.formatted(window + 1, "INV", "0")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bookingWindowDays").value(window + 1))
                    .andExpect(jsonPath("$.timeZone").value(zone));
            String details = jdbc.queryForObject("SELECT details FROM audit_logs WHERE action = 'SETTINGS_UPDATED' ORDER BY id DESC LIMIT 1", String.class);
            assertThat(details).contains("bookingWindowDays");
        } finally {
            jdbc.update("UPDATE hospital_settings SET booking_window_days = ?, invoice_prefix = 'INV', tax_rate_percent = 0, "
                    + "hospital_name = 'MediCare Community Hospital' WHERE id = 1", window);
        }
        assertThat(original).contains("\"timeZone\"");
        mockMvc.perform(put("/api/admin/settings").with(csrf()).session(loginAs(Role.RECEPTIONIST)).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(window, "INV", "0"))).andExpect(status().isForbidden());
    }

    @Test
    void reportsPreviewExportCsvAndPdfWithoutClinicalData() throws Exception {
        Patient p = patient("Report Patient");
        Patient evil = patient("=HYPERLINK(evil)");
        Appointment a = appt(p, d0, 9, "COMPLETED");
        appt(evil, d0.plusDays(1), 9, "SCHEDULED");
        String from = d0.toString();
        String to = d0.plusDays(1).toString();

        mockMvc.perform(get("/api/admin/reports/APPOINTMENTS").param("from", from).param("to", to).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.columns", not(hasItem("Reason"))))
                .andExpect(jsonPath("$.rows[0][2]").value(a.getReferenceNo()));

        byte[] csv = mockMvc.perform(get("/api/admin/reports/APPOINTMENTS/export").param("format", "CSV").param("from", from).param("to", to).session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"medicare-appointments-" + from + "_" + to + ".csv\""))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Date,Time,Reference").contains(a.getReferenceNo()).contains("'=HYPERLINK(evil)")
                .doesNotContain("Private reason");

        byte[] pdf = mockMvc.perform(get("/api/admin/reports/PAYMENTS/export").param("format", "PDF").session(admin))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        for (String type : new String[]{"INVOICES", "LAB_WORKLOAD", "INVENTORY", "AUDIT"}) {
            mockMvc.perform(get("/api/admin/reports/" + type).session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.columns").isArray());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'REPORT_EXPORTED' AND entity_id IN ('APPOINTMENTS','PAYMENTS') "
                + "AND created_at > NOW() - INTERVAL 1 MINUTE", Integer.class)).isGreaterThanOrEqualTo(2);

        mockMvc.perform(get("/api/admin/reports/NOPE").session(admin)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/reports/AUDIT/export").param("format", "JSON").session(admin))
                .andExpect(jsonPath("$.code").value("INVALID_FORMAT"));
        for (Role role : new Role[]{Role.RECEPTIONIST, Role.DOCTOR, Role.PATIENT}) {
            MockHttpSession s = loginAs(role);
            mockMvc.perform(get("/api/admin/reports/APPOINTMENTS").session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/dashboard").session(s)).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/admin/audit").session(s)).andExpect(status().isForbidden());
        }
    }

    @Test
    void auditViewerFiltersByUserActionAndOutcome() throws Exception {
        String email = userRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).reduce((x, y) -> y).orElseThrow().getEmail();
        mockMvc.perform(get("/api/admin/settings").session(admin));
        mockMvc.perform(get("/api/admin/audit").param("action", "login").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value(org.hamcrest.Matchers.containsString("LOGIN")));
        mockMvc.perform(get("/api/admin/audit").param("actor", email.substring(0, email.indexOf('@'))).param("outcome", "SUCCESS").session(admin))
                .andExpect(jsonPath("$.content[0].actorEmail").value(email))
                .andExpect(jsonPath("$.content[0].outcome").value("SUCCESS"));
        mockMvc.perform(get("/api/admin/audit").param("action", "%").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
