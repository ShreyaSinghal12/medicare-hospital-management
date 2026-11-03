package com.medicare.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.medicare.dto.schedule.SlotResponse;
import com.medicare.entity.DoctorAvailability;
import com.medicare.entity.DoctorLeave;
import com.medicare.enums.LeaveStatus;
import com.medicare.enums.LeaveType;

import org.junit.jupiter.api.Test;

class SlotCalculatorTest {

    static final LocalDate DATE = LocalDate.of(2030, 1, 7); // a Monday
    static final LocalDateTime BEFORE = LocalDateTime.of(2030, 1, 1, 8, 0);

    static DoctorAvailability day(String start, String end, String breakStart, String breakEnd) {
        DoctorAvailability a = new DoctorAvailability(1L, 1);
        a.update(LocalTime.parse(start), LocalTime.parse(end),
                breakStart == null ? null : LocalTime.parse(breakStart), breakEnd == null ? null : LocalTime.parse(breakEnd), true);
        return a;
    }

    static DoctorLeave approved(LeaveType type, String from, String to) throws Exception {
        Constructor<DoctorLeave> c = DoctorLeave.class.getDeclaredConstructor();
        c.setAccessible(true);
        DoctorLeave l = new DoctorLeave(null, type, DATE, DATE,
                from == null ? null : LocalTime.parse(from), to == null ? null : LocalTime.parse(to), null);
        l.setStatus(LeaveStatus.APPROVED);
        return l;
    }

    @Test
    void generatesSlotsAndSkipsTheBreak() {
        List<SlotResponse> slots = SlotCalculator.slots(day("09:00", "12:00", "10:00", "10:30"), 30, DATE, List.of(), List.of(), BEFORE);
        assertThat(slots).extracting(s -> s.startTime().toString())
                .containsExactly("09:00", "09:30", "10:30", "11:00", "11:30");
        assertThat(slots).allMatch(SlotResponse::isAvailable);
    }

    @Test
    void doesNotCreateASlotThatWouldEndAfterWorkingHours() {
        List<SlotResponse> slots = SlotCalculator.slots(day("09:00", "10:10", null, null), 20, DATE, List.of(), List.of(), BEFORE);
        assertThat(slots).extracting(s -> s.endTime().toString()).containsExactly("09:20", "09:40", "10:00");
    }

    @Test
    void marksBookedPastAndUnavailableSlots() throws Exception {
        var taken = List.of(new SlotCalculator.Interval(LocalTime.of(9, 15), LocalTime.of(9, 30)));
        var leave = List.of(approved(LeaveType.UNAVAILABLE, "11:00", "12:00"));
        LocalDateTime now = LocalDateTime.of(DATE, LocalTime.of(10, 0));
        List<SlotResponse> slots = SlotCalculator.slots(day("09:00", "12:00", null, null), 15, DATE, leave, taken, now);
        assertThat(slots.get(0).status()).isEqualTo(SlotCalculator.PAST);          // 09:00
        assertThat(slots.get(1).status()).isEqualTo(SlotCalculator.BOOKED);        // 09:15 (booked wins over past)
        assertThat(slots.get(4).status()).isEqualTo(SlotCalculator.PAST);          // 10:00 = now → not bookable
        assertThat(slots.get(5).status()).isEqualTo(SlotCalculator.AVAILABLE);     // 10:15
        assertThat(slots.get(8).status()).isEqualTo(SlotCalculator.UNAVAILABLE);   // 11:00 unavailability
    }

    @Test
    void fitsScheduleChecksHoursBreakAndLeave() throws Exception {
        DoctorAvailability d = day("09:00", "17:00", "13:00", "14:00");
        assertThat(SlotCalculator.fitsSchedule(d, DATE, LocalTime.of(9, 0), LocalTime.of(9, 15), List.of())).isTrue();
        assertThat(SlotCalculator.fitsSchedule(d, DATE, LocalTime.of(13, 0), LocalTime.of(13, 15), List.of())).isFalse();
        assertThat(SlotCalculator.fitsSchedule(d, DATE, LocalTime.of(16, 50), LocalTime.of(17, 5), List.of())).isFalse();
        assertThat(SlotCalculator.fitsSchedule(d, DATE, LocalTime.of(9, 0), LocalTime.of(9, 15),
                List.of(approved(LeaveType.LEAVE, null, null)))).isFalse();
        assertThat(SlotCalculator.fitsSchedule(null, DATE, LocalTime.of(9, 0), LocalTime.of(9, 15), List.of())).isFalse();
    }
}
