package com.medicare.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The React app calls this once on start-up. Reading the token forces Spring
 * Security to write the XSRF-TOKEN cookie, which Axios then sends back as the
 * X-XSRF-TOKEN header on POST/PUT/PATCH/DELETE requests.
 */
@RestController
@Tag(name = "Authentication")
public class CsrfController {

    @GetMapping("/api/auth/csrf")
    @Operation(summary = "Initialise the CSRF cookie")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }
}
