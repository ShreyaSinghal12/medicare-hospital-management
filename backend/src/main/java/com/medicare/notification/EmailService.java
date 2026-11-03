package com.medicare.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.UUID;

import com.medicare.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text e-mails.
 *
 * <ul>
 *   <li>{@code MAIL_ENABLED=true}: real SMTP via Spring Mail.</li>
 *   <li>{@code MAIL_ENABLED=false} and {@code MAIL_OUTBOX_DIR} set (dev profile default):
 *       each e-mail is written as a .txt file to that folder, so features such as
 *       password reset can be tested without an SMTP server.</li>
 *   <li>Otherwise e-mails are skipped (in-app notifications only).</li>
 * </ul>
 * E-mail bodies must never contain diagnoses, prescriptions or other clinical details.
 * Recipient addresses and bodies are never written to the application log.
 */
@Service
public class EmailService {

    /** Result of an attempt to deliver an e-mail. */
    public enum Result { SENT, WRITTEN_TO_OUTBOX, SKIPPED, FAILED }

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final AppProperties.Mail settings;
    private final ObjectProvider<JavaMailSender> mailSender;

    public EmailService(AppProperties appProperties, ObjectProvider<JavaMailSender> mailSender) {
        this.settings = appProperties.mail();
        this.mailSender = mailSender;
    }

    public Result send(String to, String subject, String body) {
        if (settings.enabled()) {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null) {
                log.warn("MAIL_ENABLED=true but no mail sender is configured; e-mail skipped");
                return Result.FAILED;
            }
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(settings.from());
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                sender.send(message);
                return Result.SENT;
            } catch (MailException ex) {
                log.warn("E-mail delivery failed: {}", ex.getClass().getSimpleName());
                return Result.FAILED;
            }
        }
        if (settings.outboxDir() != null && !settings.outboxDir().isBlank()) {
            return writeToOutbox(to, subject, body);
        }
        return Result.SKIPPED;
    }

    private Result writeToOutbox(String to, String subject, String body) {
        try {
            Path dir = Path.of(settings.outboxDir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String fileName = FILE_TS.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
            String content = "From: " + settings.from() + "\nTo: " + to + "\nSubject: " + subject
                    + "\nDate: " + Instant.now() + "\n\n" + body + "\n";
            Files.writeString(dir.resolve(fileName), content, StandardCharsets.UTF_8);
            log.info("E-mail written to local outbox file {}", fileName);
            return Result.WRITTEN_TO_OUTBOX;
        } catch (IOException ex) {
            log.warn("Could not write e-mail to outbox: {}", ex.getClass().getSimpleName());
            return Result.FAILED;
        }
    }
}
