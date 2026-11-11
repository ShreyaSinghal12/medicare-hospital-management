package com.medicare.dto.lab;

import java.time.Instant;

/** Metadata of the uploaded PDF (the stored file name and path are never exposed). */
public record LabReportInfo(
        String fileName,
        long sizeBytes,
        Instant uploadedAt,
        String uploadedBy,
        boolean finalized,
        Instant finalizedAt) {
}
