package com.helpdesk.infrastructure.ticket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the external Helpdesk ticketing endpoint. The URL is expected
 * to come from configuration (e.g. {@code helpdesk.ticket.endpoint}) and defaults
 * to blank — when blank the {@link RestTicketAdapter} degrades to a no-op (like the
 * LLM off-mode) and escalation stays internal-only.
 */
@ConfigurationProperties(prefix = "helpdesk.ticket")
public record HelpdeskTicketProperties(
        String endpoint // full URL to POST an escalated case to; blank => no-op
) {
    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank();
    }
}
