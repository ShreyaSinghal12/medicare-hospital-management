package com.medicare;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the MediCare backend.
 *
 * <p>The JVM default time zone is pinned to UTC so that every {@code Instant}
 * written to MySQL is stored in UTC. The hospital's local time zone (used for
 * appointment dates and times) is configured in {@code hospital_settings}.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MedicalManagementApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(MedicalManagementApplication.class, args);
    }
}
