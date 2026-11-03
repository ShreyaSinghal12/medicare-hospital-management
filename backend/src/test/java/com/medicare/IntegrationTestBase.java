package com.medicare;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.medicare.entity.Department;
import com.medicare.entity.Doctor;
import com.medicare.entity.User;
import com.medicare.enums.Role;
import com.medicare.repository.DepartmentRepository;
import com.medicare.repository.DoctorRepository;
import com.medicare.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Shared helpers for MySQL-backed integration tests: unique data, login, fixtures. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Secure@123";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected DoctorRepository doctorRepository;
    @Autowired protected DepartmentRepository departmentRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    private static String cachedHash;

    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String uniqueEmail(String prefix) {
        return unique(prefix).toLowerCase() + "@test.example";
    }

    /** BCrypt is slow by design; reuse one hash of PASSWORD for fixture users. */
    protected String passwordHash() {
        if (cachedHash == null) {
            cachedHash = passwordEncoder.encode(PASSWORD);
        }
        return cachedHash;
    }

    protected User createUser(Role role, String fullName) {
        return userRepository.save(new User(uniqueEmail(role.name().toLowerCase()), passwordHash(), fullName,
                "+91 90000 12345", role));
    }

    protected Department createDepartment(String name) {
        return departmentRepository.save(new Department(name, "Test department"));
    }

    protected Doctor createDoctor(String fullName, Department dept, String specialization, String fee) {
        User user = createUser(Role.DOCTOR, fullName);
        return doctorRepository.save(new Doctor(user, dept, specialization, "MBBS", unique("LIC").toUpperCase(),
                5, new BigDecimal(fee), 15));
    }

    protected MockHttpSession login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected MockHttpSession loginAs(Role role) throws Exception {
        return login(createUser(role, "Test " + role.name().charAt(0) + role.name().substring(1).toLowerCase()).getEmail());
    }
}
