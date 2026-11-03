package com.medicare.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.medicare.exception.ApiError;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes JSON 401/403 responses instead of redirecting to a login page,
 * because the client is a single-page React application.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public JsonSecurityErrorHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean revoked = Boolean.TRUE.equals(request.getAttribute(SessionValidationFilter.REVOKED_ATTRIBUTE));
        write(response, ApiError.of(401, "Unauthorized",
                revoked ? "SESSION_REVOKED" : "AUTHENTICATION_REQUIRED",
                revoked ? "Your session has ended because your account changed. Please log in again."
                        : "Please log in to continue",
                request.getRequestURI()));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        boolean csrf = ex instanceof CsrfException;
        write(response, ApiError.of(403, "Forbidden",
                csrf ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED",
                csrf ? "Your security token is missing or expired. Please reload the page and try again."
                        : "You do not have permission to access this resource",
                request.getRequestURI()));
    }

    private void write(HttpServletResponse response, ApiError body) throws IOException {
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
