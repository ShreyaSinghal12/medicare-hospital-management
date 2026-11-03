package com.medicare.security;

import java.util.List;

import com.medicare.config.AppProperties;
import com.medicare.repository.UserRepository;

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
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
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
 * server, so revocation is immediate. {@link SessionValidationFilter} additionally
 * revokes sessions of deactivated users or after a password change.</p>
 *
 * <p><b>CSRF:</b> because authentication is cookie-based, CSRF protection is on.
 * {@code csrf.spa()} issues a readable {@code XSRF-TOKEN} cookie which Axios
 * echoes back in the {@code X-XSRF-TOKEN} header on every state-changing request.
 * The token is rotated on login and logout ({@link SessionManager}).</p>
 *
 * <p>URL rules here are the coarse first line of defence; fine-grained,
 * resource-level checks (e.g. "is this the patient's own record?") are enforced
 * in the service layer and with {@code @PreAuthorize}.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JsonSecurityErrorHandler errorHandler;
    private final AppProperties appProperties;
    private final UserRepository userRepository;

    public SecurityConfig(JsonSecurityErrorHandler errorHandler, AppProperties appProperties,
                          UserRepository userRepository) {
        this.errorHandler = errorHandler;
        this.appProperties = appProperties;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                .securityContext(sc -> sc.securityContextRepository(securityContextRepository))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fix -> fix.changeSessionId()))
                .addFilterAfter(new SessionValidationFilter(userRepository), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // --- Public ---
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/public/**",
                                "/api/auth/csrf", "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // --- Role areas (coarse); services add record-level checks ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/patients/me/**", "/api/patients/me").hasRole("PATIENT")
                        // --- Everything else under /api requires a logged-in session ---
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
                .logout(logout -> logout.disable()); // JSON logout is POST /api/auth/logout (AuthController)

        return http.build();
    }

    /** Security context stored in the HTTP session (plus request attribute for the current request). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /** Readable XSRF-TOKEN cookie (JavaScript must read it to send the header). */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(c -> c.sameSite("Lax"));
        return repository;
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
