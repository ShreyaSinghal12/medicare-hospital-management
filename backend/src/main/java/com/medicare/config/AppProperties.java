package com.medicare.config;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed application settings bound from the {@code medicare.*} keys in
 * application.yml (which in turn read environment variables).
 *
 * @param frontendBaseUrl    public URL of the React app, used in e-mail links (e.g. password reset)
 * @param corsAllowedOrigins exact origins allowed to call the API with credentials
 * @param uploadDir          directory where uploaded lab report PDFs are stored
 * @param mail               outbound e-mail settings
 * @param security           login protection settings
 */
@Validated
@ConfigurationProperties(prefix = "medicare")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @NotEmpty List<String> corsAllowedOrigins,
        @NotBlank String uploadDir,
        @NotNull @Valid Mail mail,
        @NotNull @Valid Security security) {

    /**
     * @param enabled   true = send real e-mails via SMTP (spring.mail.*)
     * @param from      sender address used for outgoing e-mails
     * @param outboxDir when SMTP is disabled and this is set, e-mails are written as .txt files to this
     *                  directory instead (local development "mailbox"). Leave empty in production.
     */
    public record Mail(boolean enabled, String from, String outboxDir) {
    }

    /**
     * @param maxFailedLogins        failed attempts before the account is temporarily locked
     * @param lockoutMinutes         lock duration
     * @param loginRequestsPerWindow max login requests per client IP per window
     * @param resetRequestsPerWindow max forgot/reset-password requests per client IP per window
     * @param rateLimitWindowMinutes sliding window length
     * @param resetTokenMinutes      validity of a password reset link
     * @param accountSetupHours      validity of the "set your password" link e-mailed to new staff accounts
     */
    public record Security(
            @Min(1) int maxFailedLogins,
            @Min(1) int lockoutMinutes,
            @Min(1) int loginRequestsPerWindow,
            @Min(1) int resetRequestsPerWindow,
            @Min(1) int rateLimitWindowMinutes,
            @Min(5) int resetTokenMinutes,
            @Min(1) int accountSetupHours) {
    }
}
