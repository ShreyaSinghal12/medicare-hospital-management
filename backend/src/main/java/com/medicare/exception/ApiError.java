package com.medicare.exception;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error body returned by every API endpoint.
 *
 * <pre>
 * { "timestamp": "...", "status": 400, "error": "Bad Request",
 *   "code": "VALIDATION_FAILED", "message": "...", "path": "/api/...",
 *   "fieldErrors": [ { "field": "email", "message": "must be a well-formed email address" } ] }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, List.of());
    }
}
