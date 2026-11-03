package com.medicare.validation;

/** Implemented by request DTOs that carry a new password and its confirmation. */
public interface PasswordConfirmation {

    String newPasswordValue();

    String confirmPassword();
}
