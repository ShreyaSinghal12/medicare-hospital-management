package com.medicare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medicare.enums.Role;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Phase 14: one place that checks every role against every protected area of the API. "Allowed" means the request
 * got past authentication and authorization (any status except 401/403; 404 or 422 are fine because the ids and
 * bodies are placeholders). "Denied" means 401 for anonymous and 403 for logged-in users.
 */
class AccessMatrixIntegrationTest extends IntegrationTestBase {

    private static final Set<Role> NONE = Set.of();
    private static final Set<Role> ANY = Set.of(Role.PATIENT, Role.DOCTOR, Role.RECEPTIONIST, Role.ADMIN);

    private record Rule(String label, MockHttpServletRequestBuilder request, Set<Role> allowed, boolean publicEndpoint) {
    }

    private static Rule get_(String url, Set<Role> allowed) {
        return new Rule("GET " + url, get(url), allowed, false);
    }

    private static Rule post_(String url, Set<Role> allowed) {
        return new Rule("POST " + url, post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"), allowed, false);
    }

    private static Rule public_(String url) {
        return new Rule("GET " + url, get(url), ANY, true);
    }

    private static final List<Rule> RULES = List.of(
            // public
            public_("/api/health"), public_("/api/public/hospital-info"), public_("/api/doctors"),
            // own account and notifications: everyone logged in
            get_("/api/account", ANY), get_("/api/account/notification-preferences", ANY), get_("/api/notifications", ANY),
            // patient area
            get_("/api/patients/me", Set.of(Role.PATIENT)), get_("/api/patients/me/medical-history", Set.of(Role.PATIENT)),
            get_("/api/patients/me/prescriptions", Set.of(Role.PATIENT)), get_("/api/patients/me/lab-orders", Set.of(Role.PATIENT)),
            get_("/api/patients/me/invoices", Set.of(Role.PATIENT)),
            // doctor area
            get_("/api/doctor/dashboard", Set.of(Role.DOCTOR)), get_("/api/doctor/patients", Set.of(Role.DOCTOR)),
            get_("/api/doctor/queue", Set.of(Role.DOCTOR)), get_("/api/doctor/prescriptions", Set.of(Role.DOCTOR)),
            get_("/api/doctor/lab-orders", Set.of(Role.DOCTOR)), post_("/api/consultations", Set.of(Role.DOCTOR)),
            post_("/api/prescriptions", Set.of(Role.DOCTOR)), post_("/api/lab-orders", Set.of(Role.DOCTOR)),
            get_("/api/lab-tests", Set.of(Role.DOCTOR, Role.ADMIN)),
            // clinical reads by id: doctors and patients pass the URL check, the service then checks the record
            get_("/api/consultations/999999999", Set.of(Role.DOCTOR, Role.PATIENT)),
            get_("/api/prescriptions/999999999", Set.of(Role.DOCTOR, Role.PATIENT)),
            // reception area (administrators may also use the front-desk API)
            get_("/api/reception/dashboard", Set.of(Role.RECEPTIONIST, Role.ADMIN)),
            get_("/api/reception/patients", Set.of(Role.RECEPTIONIST, Role.ADMIN)),
            get_("/api/reception/queue", Set.of(Role.RECEPTIONIST, Role.ADMIN)),
            // billing
            get_("/api/billing/invoices", Set.of(Role.RECEPTIONIST, Role.ADMIN)),
            post_("/api/billing/invoices", Set.of(Role.RECEPTIONIST)),
            get_("/api/billing/patients/999999999/billable", Set.of(Role.RECEPTIONIST)),
            post_("/api/billing/invoices/999999999/payments", Set.of(Role.RECEPTIONIST)),
            // administration
            get_("/api/admin/users", Set.of(Role.ADMIN)), get_("/api/admin/doctors", Set.of(Role.ADMIN)),
            get_("/api/admin/staff", Set.of(Role.ADMIN)), get_("/api/admin/lab-tests", Set.of(Role.ADMIN)),
            get_("/api/admin/inventory/medicines", Set.of(Role.ADMIN)), get_("/api/admin/inventory/alerts", Set.of(Role.ADMIN)),
            get_("/api/admin/dispensing", Set.of(Role.ADMIN)), post_("/api/admin/dispensing", Set.of(Role.ADMIN)),
            get_("/api/admin/billing/summary", Set.of(Role.ADMIN)), get_("/api/admin/dashboard", Set.of(Role.ADMIN)),
            get_("/api/admin/settings", Set.of(Role.ADMIN)), get_("/api/admin/reports/AUDIT", Set.of(Role.ADMIN)),
            get_("/api/admin/audit", Set.of(Role.ADMIN)), get_("/api/admin/reminders", Set.of(Role.ADMIN)),
            // nobody may reach an unknown area
            get_("/internal/anything", NONE));

    @Test
    void everyRoleGetsExactlyTheAccessItShould() throws Exception {
        Map<Role, MockHttpSession> sessions = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            sessions.put(role, loginAs(role));
        }
        List<String> wrong = new ArrayList<>();
        int checks = 0;
        for (Rule rule : RULES) {
            int anon = mockMvc.perform(rule.request()).andReturn().getResponse().getStatus();
            checks++;
            if (rule.publicEndpoint() ? (anon == 401 || anon == 403) : (anon != 401 && anon != 403)) {
                wrong.add("anonymous " + rule.label() + " -> " + anon);
            }
            for (Role role : Role.values()) {
                int status = mockMvc.perform(rule.request().session(sessions.get(role))).andReturn().getResponse().getStatus();
                checks++;
                boolean passed = status != 401 && status != 403;
                if (passed != rule.allowed().contains(role)) {
                    wrong.add(role + " " + rule.label() + " -> " + status + (rule.allowed().contains(role) ? " (should be allowed)" : " (should be denied)"));
                }
            }
        }
        assertThat(wrong).as("%d checks", checks).isEmpty();
        assertThat(checks).isEqualTo(RULES.size() * (Role.values().length + 1));
    }
}
