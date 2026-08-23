package com.helpdesk.infrastructure.ticket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.domain.model.SupportCase;
import com.helpdesk.domain.port.TicketPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * REST adapter that forwards an escalated {@link SupportCase} to the configured
 * Helpdesk endpoint ({@code helpdesk.ticket.endpoint}). When the endpoint is unset
 * the adapter degrades gracefully: it logs and returns {@code null} (no-op), exactly
 * like the LLM port in off-mode. On any transport/parse failure it logs and returns
 * {@code null} so an escalation is never blocked by a ticketing outage.
 *
 * <p>The provider response is expected to carry the external id in a field named
 * {@code id}, {@code ticketId}, {@code externalId}, {@code externalRef}, or
 * {@code reference}; a plain (non-JSON) body is used verbatim as the reference.
 */
@Component
@Slf4j
public class RestTicketAdapter implements TicketPort {

    private static final List<String> REF_FIELDS =
            List.of("id", "ticketId", "externalId", "externalRef", "reference");

    private final HelpdeskTicketProperties props;
    private final RestClient client;
    private final ObjectMapper mapper;

    @Autowired
    public RestTicketAdapter(HelpdeskTicketProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = RestClient.builder().build();
    }

    /** Constructor that accepts a pre-built {@link RestClient} (used by tests to inject a mock transport). */
    public RestTicketAdapter(HelpdeskTicketProperties props, ObjectMapper mapper, RestClient client) {
        this.props = props;
        this.mapper = mapper;
        this.client = (client != null) ? client : RestClient.builder().build();
    }

    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public String raiseTicket(SupportCase supportCase) {
        if (!isConfigured()) {
            log.info("Ticket endpoint not configured; skipping external ticket creation for case {} (internal-only escalation)",
                    supportCase.getReference());
            return null;
        }
        try {
            String body = mapper.writeValueAsString(TicketRequest.from(supportCase));
            String raw = client.post()
                    .uri(props.endpoint())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseRef(raw);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Ticket forward failed for case {}; continuing without external ref: {}",
                    supportCase.getReference(), ex.getMessage());
            return null;
        }
    }

    private String parseRef(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode root = mapper.readTree(raw);
            for (String field : REF_FIELDS) {
                JsonNode n = root.path(field);
                if (!n.isMissingNode() && !n.isNull() && !n.asText().isBlank()) {
                    return n.asText();
                }
            }
            return null;
        } catch (JsonProcessingException ex) {
            // Non-JSON response: use the body verbatim as the reference.
            return raw.trim();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TicketRequest(
            String reference,
            Long conversationId,
            String hotelId,
            String employee,
            String problem,
            String sopId,
            String sopTitle,
            String status,
            String failedStepKey,
            String escalationReason
    ) {
        static TicketRequest from(SupportCase c) {
            return new TicketRequest(
                    c.getReference(), c.getConversationId(), c.getHotelId(), c.getEmployee(),
                    c.getProblem(), c.getSopId(), c.getSopTitle(),
                    c.getStatus() == null ? null : c.getStatus().name(),
                    c.getFailedStepKey(), c.getEscalationReason());
        }
    }
}
