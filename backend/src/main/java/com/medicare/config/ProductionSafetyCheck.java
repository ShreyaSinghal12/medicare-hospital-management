package com.medicare.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start the {@code prod} profile with settings that are only acceptable on a developer machine.
 * Problems are listed by name; secret values are never printed.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);
    static final int MIN_DB_PASSWORD_LENGTH = 12;

    private final Environment env;

    public ProductionSafetyCheck(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void verify() {
        List<String> problems = problems(env);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration:\n - " + String.join("\n - ", problems));
        }
        if (!env.getProperty("medicare.mail.enabled", Boolean.class, false)) {
            log.warn("MAIL_ENABLED is false: password-reset and account-setup e-mails will not be delivered");
        }
        log.info("Production configuration checks passed");
    }

    /** All problems found (empty when the configuration is acceptable). */
    static List<String> problems(Environment env) {
        List<String> p = new ArrayList<>();
        if (Arrays.asList(env.getActiveProfiles()).contains("demo")) {
            p.add("the demo profile (fictional accounts with a shared password) is active");
        }
        if (!env.getProperty("server.servlet.session.cookie.secure", Boolean.class, false)) {
            p.add("session cookie is not marked Secure (COOKIE_SECURE / server.servlet.session.cookie.secure)");
        }
        String frontend = env.getProperty("medicare.frontend-base-url", "");
        if (!frontend.startsWith("https://")) {
            p.add("FRONTEND_BASE_URL must use https:// (links in e-mails)");
        }
        for (String origin : env.getProperty("medicare.cors-allowed-origins", "").split(",")) {
            String o = origin.trim().toLowerCase(Locale.ROOT);
            if (o.isEmpty() || !o.startsWith("https://") || o.contains("*")) {
                p.add("CORS_ALLOWED_ORIGINS must list exact https:// origins (found '" + origin.trim() + "')");
            }
        }
        if (!env.getProperty("medicare.mail.outbox-dir", "").isBlank()) {
            p.add("MAIL_OUTBOX_DIR must be empty in production (e-mails would be written to disk)");
        }
        if (env.getProperty("springdoc.swagger-ui.enabled", Boolean.class, true)) {
            p.add("Swagger UI must be disabled in production");
        }
        String dbPassword = env.getProperty("spring.datasource.password", "");
        if (dbPassword.length() < MIN_DB_PASSWORD_LENGTH) {
            p.add("DB_PASSWORD must be at least " + MIN_DB_PASSWORD_LENGTH + " characters");
        }
        if (!env.getProperty("spring.datasource.url", "").contains("mysql")) {
            p.add("DB_URL must point to the MySQL database");
        }
        return p;
    }
}
