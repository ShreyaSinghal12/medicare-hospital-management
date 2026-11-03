package com.medicare.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.medicare.exception.TooManyRequestsException;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory sliding-window rate limiter keyed by e.g. "login:203.0.113.5".
 *
 * <p>Suitable for a single-server deployment (this project). A multi-server
 * deployment would need a shared store such as Redis.</p>
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Records one request for {@code key}; throws when more than {@code maxRequests}
     * happened within {@code window}.
     */
    public void check(String key, int maxRequests, Duration window) {
        long now = clock.millis();
        long windowStart = now - window.toMillis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                long retryAfterMs = deque.peekFirst() + window.toMillis() - now;
                throw new TooManyRequestsException("Too many attempts. Please wait and try again later.",
                        Math.max(1, retryAfterMs / 1000));
            }
            deque.addLast(now);
        }
    }

    /** Drops idle keys every 10 minutes so memory does not grow without bound. */
    @Scheduled(fixedDelay = 600_000)
    public void evictIdle() {
        long cutoff = clock.millis() - Duration.ofHours(1).toMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || last < cutoff;
            }
        });
    }
}
