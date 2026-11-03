package com.medicare.service;

import static com.medicare.enums.AppointmentStatus.CANCELLED;
import static com.medicare.enums.AppointmentStatus.CHECKED_IN;
import static com.medicare.enums.AppointmentStatus.COMPLETED;
import static com.medicare.enums.AppointmentStatus.IN_PROGRESS;
import static com.medicare.enums.AppointmentStatus.NO_SHOW;
import static com.medicare.enums.AppointmentStatus.SCHEDULED;
import static org.assertj.core.api.Assertions.assertThat;

import com.medicare.enums.AppointmentStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppointmentStatusTest {

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED,CHECKED_IN", "SCHEDULED,CANCELLED", "SCHEDULED,NO_SHOW",
            "CHECKED_IN,IN_PROGRESS", "CHECKED_IN,CANCELLED", "CHECKED_IN,NO_SHOW",
            "IN_PROGRESS,COMPLETED"})
    void allowedTransitions(AppointmentStatus from, AppointmentStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "SCHEDULED,COMPLETED", "SCHEDULED,IN_PROGRESS", "IN_PROGRESS,CANCELLED", "COMPLETED,CANCELLED",
            "CANCELLED,SCHEDULED", "NO_SHOW,CHECKED_IN", "COMPLETED,IN_PROGRESS", "CHECKED_IN,SCHEDULED"})
    void forbiddenTransitions(AppointmentStatus from, AppointmentStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void terminalStatesHaveNoExits() {
        for (AppointmentStatus target : AppointmentStatus.values()) {
            assertThat(COMPLETED.canTransitionTo(target)).isFalse();
            assertThat(CANCELLED.canTransitionTo(target)).isFalse();
            assertThat(NO_SHOW.canTransitionTo(target)).isFalse();
        }
        assertThat(SCHEDULED.occupiesSlot()).isTrue();
        assertThat(CANCELLED.occupiesSlot()).isFalse();
        assertThat(IN_PROGRESS.isOpen()).isTrue();
        assertThat(CHECKED_IN.isOpen()).isTrue();
    }
}
