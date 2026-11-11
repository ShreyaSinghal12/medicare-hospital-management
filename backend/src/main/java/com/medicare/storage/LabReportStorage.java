package com.medicare.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import com.medicare.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stores lab report PDFs on disk, outside the web root, under {@code <upload-dir>/lab-reports/}.
 *
 * <p>Files get a random server-generated name ({@code <uuid>.pdf}); the user's file name is never used as a
 * path. Every resolved path is checked to stay inside the storage directory. Files are only ever served
 * through the authorized download endpoint.</p>
 */
@Component
public class LabReportStorage {

    private static final Logger log = LoggerFactory.getLogger(LabReportStorage.class);
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{32}\\.pdf$");

    private final Path root;

    public LabReportStorage(AppProperties properties) {
        this.root = Path.of(properties.uploadDir(), "lab-reports").toAbsolutePath().normalize();
    }

    public record StoredFile(String storedName, long size, String sha256) {
    }

    public StoredFile store(byte[] content) {
        String name = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        Path target = resolve(name);
        try {
            Files.createDirectories(root);
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store the report file", ex);
        }
        return new StoredFile(name, content.length, sha256(content));
    }

    public byte[] read(String storedName) {
        try {
            return Files.readAllBytes(resolve(storedName));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the report file", ex);
        }
    }

    /** Best effort: a leftover file is logged, never fatal. */
    public void delete(String storedName) {
        if (storedName == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("Could not delete stored report file {}", storedName);
        }
    }

    public boolean exists(String storedName) {
        return Files.exists(resolve(storedName));
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Path resolve(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Path outside storage directory");
        }
        return p;
    }
}
