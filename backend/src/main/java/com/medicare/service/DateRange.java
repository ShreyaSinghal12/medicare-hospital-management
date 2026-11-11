package com.medicare.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.medicare.exception.BusinessRuleException;

/** An inclusive range of hospital-local dates, limited to 366 days, with UTC instant bounds for timestamp columns. */
public record DateRange(LocalDate from, LocalDate to, Instant start, Instant end) {

    public static final int MAX_DAYS = 366;

    /** Defaults: {@code to} = today, {@code from} = to − (defaultDays − 1). */
    public static DateRange of(LocalDate from, LocalDate to, LocalDate today, int defaultDays, ZoneId zone) {
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(defaultDays - 1L) : from;
        if (end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_DATES", "'To' must be on or after 'from'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BusinessRuleException("RANGE_TOO_LONG", "Choose a range of at most " + MAX_DAYS + " days");
        }
        return new DateRange(start, end, start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
