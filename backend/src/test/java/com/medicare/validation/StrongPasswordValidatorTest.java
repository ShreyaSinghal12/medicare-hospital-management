package com.medicare.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"Secure@123", "Abcdef1!", "Zz9#zzzzzzzz"})
    void acceptsStrongPasswords(String password) {
        assertThat(StrongPasswordValidator.isStrong(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1!A",            // exactly 8 -> valid boundary is checked separately below
    })
    void eightCharactersIsTheMinimum(String password) {
        assertThat(StrongPasswordValidator.isStrong(password)).isTrue();
        assertThat(StrongPasswordValidator.isStrong(password.substring(1))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "alllowercase1!",      // no upper
            "ALLUPPERCASE1!",      // no lower
            "NoDigitsHere!",       // no digit
            "NoSpecial123",        // no special
            "Has Space1!",         // whitespace
    })
    void rejectsWeakPasswords(String password) {
        assertThat(StrongPasswordValidator.isStrong(password)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void rejectsPasswordsLongerThan64() {
        assertThat(StrongPasswordValidator.isStrong("Aa1!" + "x".repeat(61))).isFalse();
        assertThat(StrongPasswordValidator.isStrong("Aa1!" + "x".repeat(60))).isTrue();
    }
}
