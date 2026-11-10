package com.medicare.service;

import java.security.SecureRandom;
import java.util.Base64;

import com.medicare.entity.User;
import com.medicare.enums.Role;
import com.medicare.exception.ConflictException;
import com.medicare.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates login identities for staff (doctors, receptionists, administrators). The initial
 * password is random and never shown to anyone; the new user receives a single-use
 * "set your password" link instead.
 */
@Service
public class StaffAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User createStaffUser(String email, String fullName, String phone, Role role) {
        String normalized = AuthService.normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this e-mail address already exists");
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        // 43 base64 characters: well inside BCrypt's 72-byte input limit, and never disclosed.
        String unusablePassword = passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(random));
        return userRepository.save(new User(normalized, unusablePassword, fullName.trim(), phone.trim(), role));
    }
}
