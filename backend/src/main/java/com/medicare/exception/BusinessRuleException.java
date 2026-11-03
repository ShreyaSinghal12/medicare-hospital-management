package com.medicare.exception;

/**
 * A request that is syntactically valid but violates a business rule,
 * e.g. booking a past slot or an invalid status transition (HTTP 422).
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
