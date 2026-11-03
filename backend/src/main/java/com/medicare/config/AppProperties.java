package com.medicare.config;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed application settings bound from the {@code medicare.*} keys in
 * application.yml (which in turn read environment variables).
 *
 * @param frontendBaseUrl  public URL of the React app, used in e-mail links (e.g. password reset)
 * @param corsAllowedOrigins exact origins allowed to call the API with credentials
 * @param uploadDir        directory where uploaded lab report PDFs are stored
 * @param mail             outbound e-mail settings
 */
@Validated
@ConfigurationProperties(prefix = "medicare")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @NotEmpty List<String> corsAllowedOrigins,
        @NotBlank String uploadDir,
        Mail mail) {

    /**
     * @param enabled when false the app runs in in-app-only notification mode (no SMTP needed)
     * @param from    sender address used for outgoing e-mails
     */
    public record Mail(boolean enabled, String from) {
    }
}
