package com.medicare.security;

import java.util.Optional;

import com.medicare.exception.AuthenticationFailedException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static access to the logged-in {@link AppUserPrincipal} from service code. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AppUserPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AppUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    /** @throws AuthenticationFailedException when nobody is logged in */
    public static AppUserPrincipal get() {
        return find().orElseThrow(() ->
                new AuthenticationFailedException("AUTHENTICATION_REQUIRED", "Please log in to continue"));
    }

    public static Long id() {
        return get().id();
    }
}
