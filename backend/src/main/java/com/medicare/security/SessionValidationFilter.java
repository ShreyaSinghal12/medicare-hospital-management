package com.medicare.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.medicare.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-checks the session's user against the database on every authenticated request.
 * The session is revoked immediately when:
 * <ul>
 *   <li>the account was deactivated by an administrator,</li>
 *   <li>the password was changed/reset after this session logged in, or</li>
 *   <li>the user's role changed.</li>
 * </ul>
 * This is a single primary-key lookup per request, acceptable for this project's scale.
 * Not a Spring bean on purpose, so it is not also registered as a plain servlet filter.
 */
public class SessionValidationFilter extends OncePerRequestFilter {

    public static final String REVOKED_ATTRIBUTE = "medicare.sessionRevoked";

    private final UserRepository userRepository;

    public SessionValidationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CurrentUser.find().ifPresent(principal -> {
            boolean valid = userRepository.findById(principal.id())
                    .filter(u -> u.isActive()
                            && u.getRole() == principal.role()
                            && !u.getPasswordChangedAt().isAfter(principal.authenticatedAt()))
                    .isPresent();
            if (!valid) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                request.setAttribute(REVOKED_ATTRIBUTE, Boolean.TRUE);
            }
        });
        chain.doFilter(request, response);
    }
}
