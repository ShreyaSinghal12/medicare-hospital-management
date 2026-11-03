package com.medicare.dto.clinical;

import java.time.Instant;
import java.util.Map;

/** One amendment: why, by whom, when, and the values that were replaced. */
public record AmendmentResponse(
        Long id,
        String reason,
        String amendedBy,
        Instant amendedAt,
        Map<String, Object> previousValues) {
}
