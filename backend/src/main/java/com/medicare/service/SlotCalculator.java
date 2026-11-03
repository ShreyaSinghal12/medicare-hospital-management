package com.medicare.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.medicare.dto.schedule.SlotResponse;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.DoctorLeave;

/**
 * Rule-based slot generation (no AI): pure functions, so they are easy to unit test.
 *
 * <p>Slots run from the day's start time in steps of the doctor's consultation length.
 * A slot is dropped if it overlaps the break or would end after the working day; it is
 * marked UNAVAILABLE if approved leave/unavailability covers it, BOOKED if an existing
 * appointment overlaps it, and PAST if it starts at or before "now".</p>
 */
public final class SlotCalculator {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String BOOKED = "BOOKED";
    public static final String PAST = "PAST";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    /** A time interval [start, end) already taken by an appointment. */
    public record Interval(LocalTime start, LocalTime end) {
        boolean overlaps(LocalTime s, LocalTime e) {
            return s.isBefore(end) && e.isAfter(start);
        }
    }

    private SlotCalculator() {
    }

    public static List<SlotResponse> slots(DoctorAvailability day, int durationMinutes, LocalDate date,
                                           List<DoctorLeave> approvedLeave, List<Interval> taken,
                                           LocalDateTime now) {
        List<SlotResponse> result = new ArrayList<>();
        if (day == null || !day.isActive() || durationMinutes <= 0) {
            return result;
        }
        LocalTime t = day.getStartTime();
        while (true) {
            LocalTime end = t.plusMinutes(durationMinutes);
            // stop at the end of the working day (and guard against wrapping past midnight)
            if (end.isAfter(day.getEndTime()) || !end.isAfter(t)) {
                break;
            }
            if (overlapsBreak(day, t, end)) {
                // jump to the end of the break so slots realign after lunch
                t = day.getBreakEnd().isAfter(t) ? day.getBreakEnd() : end;
                continue;
            }
            final LocalTime s = t;
            String status;
            if (approvedLeave.stream().anyMatch(l -> l.blocks(date, s, end))) {
                status = UNAVAILABLE;
            } else if (taken.stream().anyMatch(i -> i.overlaps(s, end))) {
                status = BOOKED;
            } else if (!LocalDateTime.of(date, s).isAfter(now)) {
                status = PAST;
            } else {
                status = AVAILABLE;
            }
            result.add(new SlotResponse(s, end, status));
            t = end;
        }
        return result;
    }

    /** True if [start, end) lies within working hours, outside the break and is not blocked by leave. */
    public static boolean fitsSchedule(DoctorAvailability day, LocalDate date, LocalTime start, LocalTime end,
                                       List<DoctorLeave> approvedLeave) {
        if (day == null || !day.isActive()) {
            return false;
        }
        if (start.isBefore(day.getStartTime()) || end.isAfter(day.getEndTime()) || overlapsBreak(day, start, end)) {
            return false;
        }
        return approvedLeave.stream().noneMatch(l -> l.blocks(date, start, end));
    }

    private static boolean overlapsBreak(DoctorAvailability day, LocalTime start, LocalTime end) {
        return day.getBreakStart() != null && day.getBreakEnd() != null
                && start.isBefore(day.getBreakEnd()) && end.isAfter(day.getBreakStart());
    }
}
