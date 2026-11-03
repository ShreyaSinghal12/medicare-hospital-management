package com.medicare.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client IP address of the current request. X-Forwarded-For is NOT trusted here;
 * when deploying behind a reverse proxy, set {@code server.forward-headers-strategy=native}
 * so the container rewrites getRemoteAddr() from trusted proxy headers.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    public static String current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return of(attrs.getRequest());
        }
        return null;
    }
}
