package com.medicare.validation;

/** Shared regular expressions for Bean Validation {@code @Pattern} annotations. */
public final class ValidationPatterns {

    /** Optional leading +, then 7–20 digits, spaces, hyphens or parentheses. */
    public static final String PHONE = "^\\+?[0-9 ()\\-]{7,20}$";
    public static final String PHONE_MESSAGE = "Enter a valid phone number (7-20 digits, may start with +)";

    /** Letters (any language), spaces, apostrophes, dots and hyphens. */
    public static final String PERSON_NAME = "^[\\p{L} .'\\-]{2,120}$";
    public static final String PERSON_NAME_MESSAGE = "Name may contain only letters, spaces, apostrophes, dots and hyphens";

    private ValidationPatterns() {
    }
}
