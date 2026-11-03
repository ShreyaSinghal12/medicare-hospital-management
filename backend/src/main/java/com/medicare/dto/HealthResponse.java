package com.medicare.dto;

import java.time.Instant;

/**
 * Response of GET /api/health — proves that the Java backend is running and
 * that it can reach MySQL with the Flyway schema applied.
 */
public record HealthResponse(
        String status,
        String application,
        String version,
        String javaVersion,
        Instant serverTime,
        DatabaseStatus database) {

    /**
     * @param status          UP or DOWN
     * @param product         e.g. "MySQL"
     * @param productVersion  e.g. "8.0.46"
     * @param schemaVersion   latest applied Flyway migration version
     * @param tableCount      number of tables in the schema
     * @param error           short error description when DOWN (no stack trace)
     */
    public record DatabaseStatus(
            String status,
            String product,
            String productVersion,
            String schemaVersion,
            Integer tableCount,
            String error) {
    }
}
