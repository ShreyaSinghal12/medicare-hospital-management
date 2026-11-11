package com.medicare.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyCheckTest {

    static MockEnvironment safe() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("server.servlet.session.cookie.secure", "true")
                .withProperty("medicare.frontend-base-url", "https://medicare.example")
                .withProperty("medicare.cors-allowed-origins", "https://medicare.example")
                .withProperty("medicare.mail.outbox-dir", "")
                .withProperty("springdoc.swagger-ui.enabled", "false")
                .withProperty("spring.datasource.password", "a-long-random-password")
                .withProperty("spring.datasource.url", "jdbc:mysql://db:3306/medicare_db");
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    void acceptsAHardenedConfiguration() {
        assertThat(ProductionSafetyCheck.problems(safe())).isEmpty();
    }

    @Test
    void listsEveryUnsafeSettingWithoutPrintingSecrets() {
        MockEnvironment env = safe()
                .withProperty("server.servlet.session.cookie.secure", "false")
                .withProperty("medicare.frontend-base-url", "http://localhost:5173")
                .withProperty("medicare.cors-allowed-origins", "https://medicare.example, http://localhost:5173,*")
                .withProperty("medicare.mail.outbox-dir", "./mail-outbox")
                .withProperty("springdoc.swagger-ui.enabled", "true")
                .withProperty("spring.datasource.password", "short-pw");
        env.setActiveProfiles("prod", "demo");
        var problems = ProductionSafetyCheck.problems(env);
        assertThat(problems).hasSize(8);
        assertThat(String.join("\n", problems)).doesNotContain("short-pw")
                .contains("demo profile", "Secure", "FRONTEND_BASE_URL", "http://localhost:5173", "MAIL_OUTBOX_DIR", "Swagger", "DB_PASSWORD");
    }
}
