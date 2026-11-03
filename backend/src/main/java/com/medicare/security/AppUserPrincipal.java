package com.medicare.security;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.medicare.entity.User;
import com.medicare.enums.Role;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated user stored in the HTTP session. It holds only identifiers
 * and the login time, never the password hash or clinical data.
 *
 * @param authenticatedAt when this session logged in; compared with
 *                        {@code users.password_changed_at} to revoke stale sessions
 */
public record AppUserPrincipal(
        Long id,
        String email,
        String fullName,
        Role role,
        Instant authenticatedAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AppUserPrincipal from(User user, Instant authenticatedAt) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), authenticatedAt);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    public boolean hasRole(Role r) {
        return role == r;
    }

    @Override
    public String toString() {
        return "AppUserPrincipal[id=" + id + ", role=" + role + "]"; // no e-mail/name in logs
    }
}
