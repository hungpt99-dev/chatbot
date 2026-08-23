package com.helpdesk.infrastructure.ticket;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link HelpdeskTicketProperties} and the REST {@link TicketPort}
 * implementation. When no endpoint is configured the adapter simply reports
 * {@code isConfigured() == false} and escalation degrades to internal-only.
 */
@Configuration
@EnableConfigurationProperties(HelpdeskTicketProperties.class)
public class TicketConfiguration {
}
