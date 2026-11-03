package com.medicare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables JPA auditing ({@code @CreatedDate}/{@code @LastModifiedDate}) and
 * declarative transactions.
 */
@Configuration
@EnableJpaAuditing
@EnableTransactionManagement
public class JpaConfig {
}
