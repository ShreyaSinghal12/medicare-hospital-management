package com.medicare.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;

import javax.sql.DataSource;

import com.medicare.dto.HealthResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Builds the system health report: application info plus a live MySQL check
 * (connection, server version, latest Flyway migration and table count).
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final String applicationName;
    private final String applicationVersion;

    public HealthService(DataSource dataSource,
                         JdbcTemplate jdbcTemplate,
                         @Value("${spring.application.name}") String applicationName,
                         @Value("${medicare.version:1.0.0}") String applicationVersion) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
    }

    public HealthResponse check() {
        HealthResponse.DatabaseStatus db = checkDatabase();
        String overall = "UP".equals(db.status()) ? "UP" : "DEGRADED";
        return new HealthResponse(overall, applicationName, applicationVersion,
                System.getProperty("java.version"), Instant.now(), db);
    }

    private HealthResponse.DatabaseStatus checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String schema = connection.getCatalog();
            String schemaVersion = jdbcTemplate.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL "
                            + "ORDER BY installed_rank DESC LIMIT 1", String.class);
            Integer tables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'",
                    Integer.class, schema);
            return new HealthResponse.DatabaseStatus("UP", meta.getDatabaseProductName(),
                    meta.getDatabaseProductVersion(), schemaVersion, tables, null);
        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getClass().getSimpleName());
            return new HealthResponse.DatabaseStatus("DOWN", null, null, null, null,
                    "Database is unreachable");
        }
    }
}
