package com.medicare.exception;

/**
 * Login failed (HTTP 401). Codes: INVALID_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_DISABLED.
 * Messages never reveal whether an e-mail address is registered.
 */
public class AuthenticationFailedException extends RuntimeException {

    private final String code;

    public AuthenticationFailedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
