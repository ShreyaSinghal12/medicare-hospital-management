package com.medicare;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 1 integration tests. They start the full Spring context against the
 * real MySQL test database, so Flyway migrations and Hibernate schema
 * validation are exercised too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsDatabaseUpWithMigratedSchema() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("medicare-backend"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.product").value("MySQL"))
                .andExpect(jsonPath("$.database.schemaVersion").value("10"))
                .andExpect(jsonPath("$.database.tableCount", greaterThanOrEqualTo(35)));
    }

    @Test
    void publicHospitalInfoContainsSeededDepartments() throws Exception {
        mockMvc.perform(get("/api/public/hospital-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitalName").value("MediCare Community Hospital"))
                .andExpect(jsonPath("$.departments", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.departments[?(@.name == 'Cardiology')]").exists());
    }

    @Test
    void protectedEndpointWithoutSessionReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/patients/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/appointments").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void postWithCsrfButWithoutSessionReturns401() throws Exception {
        mockMvc.perform(post("/api/appointments").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mockMvc.perform(get("/api/health").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(get("/api/health").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"));
    }
}
