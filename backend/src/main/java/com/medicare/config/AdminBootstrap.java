package com.medicare.config;

import com.medicare.entity.User;
import com.medicare.enums.AuditOutcome;
import com.medicare.enums.Role;
import com.medicare.repository.UserRepository;
import com.medicare.service.AuditService;
import com.medicare.service.AuthService;
import com.medicare.service.StaffAccountService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-run setup for a new installation: when {@code BOOTSTRAP_ADMIN_EMAIL} is set and no active administrator exists,
 * creates that administrator with a random, never-disclosed password and e-mails a single-use "set your password" link
 * (SMTP must be configured). Does nothing once an administrator exists, so the variable can stay set.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final StaffAccountService staffAccountService;
    private final AuthService authService;
    private final AuditService auditService;
    private final String email;
    private final String name;
    private final String phone;

    public AdminBootstrap(UserRepository userRepository, StaffAccountService staffAccountService, AuthService authService,
                          AuditService auditService,
                          @Value("${medicare.bootstrap.admin-email:}") String email,
                          @Value("${medicare.bootstrap.admin-name:Administrator}") String name,
                          @Value("${medicare.bootstrap.admin-phone:+91 00000 00000}") String phone) {
        this.userRepository = userRepository;
        this.staffAccountService = staffAccountService;
        this.authService = authService;
        this.auditService = auditService;
        this.email = email;
        this.name = name;
        this.phone = phone;
    }

    @Override
    @Transactional   // run() is the entry point Spring calls through the proxy; bootstrap() below is a self-call
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    /** @return true if an administrator was created */
    @Transactional
    public boolean bootstrap() {
        if (email == null || email.isBlank() || userRepository.countByRoleAndActiveTrue(Role.ADMIN) > 0) {
            return false;
        }
        User admin = staffAccountService.createStaffUser(email, name, phone, Role.ADMIN);
        authService.sendAccountSetupLink(admin);
        auditService.recordFor(null, null, "SYSTEM", "ADMIN_BOOTSTRAPPED", "User", admin.getId(), AuditOutcome.SUCCESS,
                "First administrator created from BOOTSTRAP_ADMIN_EMAIL");
        log.info("First administrator created; a set-your-password link was e-mailed to the configured address");
        return true;
    }
}
