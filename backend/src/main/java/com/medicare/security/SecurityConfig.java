package com.medicare.security;

import java.util.List;

import com.medicare.config.AppProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central security configuration.
 *
 * <p><b>Authentication approach:</b> server-side HTTP sessions identified by an
 * HTTP-only {@code MEDICARE_SESSION} cookie. No tokens are ever exposed to
 * JavaScript or stored in localStorage. Logout invalidates the session on the
 * server, so revocation is immediate.</p>
 *
 * <p><b>CSRF:</b> because authentication is cookie-based, CSRF protection is on.
 * {@code csrf.spa()} issues a readable {@code XSRF-TOKEN} cookie which Axios
 * echoes back in the {@code X-XSRF-TOKEN} header on every state-changing request.</p>
 *
 * <p>Endpoint rules here are the coarse first line of defence; fine-grained,
 * resource-level checks (e.g. "is this the patient's own record?") are enforced
 * in the service layer and with {@code @PreAuthorize}.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JsonSecurityErrorHandler errorHandler;
    private final AppProperties appProperties;

    public SecurityConfig(JsonSecurityErrorHandler errorHandler, AppProperties appProperties) {
        this.errorHandler = errorHandler;
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.spa())
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fix -> fix.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/public/**", "/api/auth/csrf").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Everything else under /api requires an authenticated session
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none'; "
                                        + "img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'"))
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .frameOptions(fo -> fo.deny()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable()); // custom JSON logout endpoint is added in Phase 2

        return http.build();
    }

    /** BCrypt with cost factor 12 (~250 ms per hash on a laptop): slow enough to resist brute force. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Only the configured frontend origins may send credentialed requests. Never "*". */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.corsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Requested-With", "Accept"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
