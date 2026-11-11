package com.medicare.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicare.entity.User;
import com.medicare.enums.Role;
import com.medicare.repository.UserRepository;
import com.medicare.service.AuditService;
import com.medicare.service.AuthService;
import com.medicare.service.StaffAccountService;

import org.junit.jupiter.api.Test;

class AdminBootstrapTest {

    UserRepository users = mock(UserRepository.class);
    StaffAccountService staff = mock(StaffAccountService.class);
    AuthService auth = mock(AuthService.class);
    AuditService audit = mock(AuditService.class);

    AdminBootstrap bootstrap(String email) {
        return new AdminBootstrap(users, staff, auth, audit, email, "First Admin", "+91 90000 00000");
    }

    @Test
    void createsTheFirstAdministratorAndSendsTheSetupLink() {
        User admin = new User("first@hospital.example", "x", "First Admin", "+91 90000 00000", Role.ADMIN);
        when(users.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(0L);
        when(staff.createStaffUser("first@hospital.example", "First Admin", "+91 90000 00000", Role.ADMIN)).thenReturn(admin);
        assertThat(bootstrap("first@hospital.example").bootstrap()).isTrue();
        verify(auth).sendAccountSetupLink(admin);
    }

    @Test
    void doesNothingWhenAnAdministratorExistsOrNoAddressIsConfigured() {
        when(users.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);
        assertThat(bootstrap("first@hospital.example").bootstrap()).isFalse();
        assertThat(bootstrap("").bootstrap()).isFalse();
        verify(staff, never()).createStaffUser(any(), any(), any(), any());
    }
}
