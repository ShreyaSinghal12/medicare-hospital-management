package com.medicare.dto.auth;

import com.medicare.validation.PasswordConfirmation;
import com.medicare.validation.PasswordsMatch;
import com.medicare.validation.StrongPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordsMatch
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @StrongPassword String newPassword,
        @NotBlank String confirmPassword) implements PasswordConfirmation {

    @Override
    public String newPasswordValue() {
        return newPassword;
    }
}
