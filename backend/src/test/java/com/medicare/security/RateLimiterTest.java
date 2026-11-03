package com.medicare.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.medicare.exception.TooManyRequestsException;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** Mutable clock so the test controls time. */
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void blocksAfterLimitAndRecoversAfterWindow() {
        TestClock clock = new TestClock();
        RateLimiter limiter = new RateLimiter(clock);
        Duration window = Duration.ofMinutes(15);

        for (int i = 0; i < 3; i++) {
            limiter.check("login:1.2.3.4", 3, window);
        }
        assertThatThrownBy(() -> limiter.check("login:1.2.3.4", 3, window))
                .isInstanceOf(TooManyRequestsException.class);

        // a different IP is unaffected
        assertThatCode(() -> limiter.check("login:5.6.7.8", 3, window)).doesNotThrowAnyException();

        clock.now = clock.now.plus(window).plusSeconds(1);
        assertThatCode(() -> limiter.check("login:1.2.3.4", 3, window)).doesNotThrowAnyException();
    }
}
