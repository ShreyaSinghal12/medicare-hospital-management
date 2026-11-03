package com.medicare.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata. Swagger UI is served at /swagger-ui.html (dev profile only).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI medicareOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MediCare API")
                .version("1.0.0")
                .description("""
                        REST API of the MediCare Smart Medical Management System (academic prototype).
                        Authentication uses an HTTP-only session cookie. State-changing requests must send
                        the X-XSRF-TOKEN header with the value of the XSRF-TOKEN cookie
                        (call GET /api/auth/csrf first).""")
                .contact(new Contact().name("MediCare project team"))
                .license(new License().name("Academic use")));
    }
}
