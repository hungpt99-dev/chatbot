package com.helpdesk.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables {@code @PreAuthorize} enforcement on the web controllers. Activated
 * only when {@code helpdesk.security.enabled=true} so the RBAC annotations are
 * inert while security is disabled (keeping the unauthenticated suite green and
 * local dev open).
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "helpdesk.security", name = "enabled", havingValue = "true")
public class MethodSecurityConfig {
}
