package com.medicare.dto.account;

import com.medicare.validation.PasswordConfirmation;
import com.medicare.validation.PasswordsMatch;
import com.medicare.validation.StrongPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@PasswordsMatch
public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @StrongPassword String newPassword,
        @NotBlank String confirmPassword) implements PasswordConfirmation {

    @Override
    public String newPasswordValue() {
        return newPassword;
    }
}
