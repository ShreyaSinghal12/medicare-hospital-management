package com.medicare.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Implements {@link StrongPassword}. A null value is left to {@code @NotBlank}. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return isStrong(value);
    }

    /** BCrypt only uses the first 72 bytes of its input, and Spring Security rejects longer input. */
    public static final int MAX_BCRYPT_BYTES = 72;

    public static boolean isStrong(String value) {
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH
                || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            return false;
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char c : value.toCharArray()) {
            if (Character.isWhitespace(c)) {
                return false;
            } else if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else {
                special = true;
            }
        }
        return upper && lower && digit && special;
    }
}
