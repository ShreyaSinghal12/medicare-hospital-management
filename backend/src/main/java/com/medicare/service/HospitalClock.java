package com.medicare.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * "Now" in the hospital's configured time zone. Appointment dates and times are local to
 * the hospital, so all scheduling decisions (past slots, booking window, cancellation window)
 * go through this class instead of the server's default zone.
 */
@Component
public class HospitalClock {

    private final HospitalSettingsService settingsService;
    private final Clock clock;

    @Autowired
    public HospitalClock(HospitalSettingsService settingsService) {
        this(settingsService, Clock.systemUTC());
    }

    HospitalClock(HospitalSettingsService settingsService, Clock clock) {
        this.settingsService = settingsService;
        this.clock = clock;
    }

    public ZoneId zone() {
        return settingsService.getZoneId();
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(zone()));
    }

    public LocalDate today() {
        return now().toLocalDate();
    }
}
