package com.medicare.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Class-level constraint: the password and its confirmation must be equal.
 * The error is reported on the {@code confirmPassword} field so the UI can show it there.
 */
@Documented
@Constraint(validatedBy = PasswordsMatch.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordsMatch {

    String message() default "Passwords do not match";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<PasswordsMatch, PasswordConfirmation> {
        @Override
        public boolean isValid(PasswordConfirmation value, ConstraintValidatorContext context) {
            if (value == null || value.newPasswordValue() == null || value.confirmPassword() == null) {
                return true; // @NotBlank on the fields reports missing values
            }
            if (value.newPasswordValue().equals(value.confirmPassword())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
            return false;
        }
    }
}
