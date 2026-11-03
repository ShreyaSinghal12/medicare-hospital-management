package com.medicare.exception;

/**
 * The request conflicts with the current state of a resource, e.g. a duplicate
 * e-mail or an already reserved appointment slot (HTTP 409).
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
