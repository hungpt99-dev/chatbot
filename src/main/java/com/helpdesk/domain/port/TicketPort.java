package com.helpdesk.domain.port;

import com.helpdesk.domain.model.SupportCase;

/**
 * Boundary for forwarding an escalated {@link SupportCase} to the external
 * Helpdesk/ticketing system and obtaining the provider-side ticket reference.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #raiseTicket(SupportCase)} returns the external ticket
 *       reference, or {@code null} when no endpoint is configured (off-mode) or the
 *       forward fails. Callers must treat {@code null} as "no external ticket" and
 *       must not block the escalation on a ticketing outage.</li>
 *   <li>The adapter must never throw into the request path on a transient provider
 *       error; it degrades to {@code null}, exactly like the LLM off-mode path.</li>
 * </ul>
 */
public interface TicketPort {

    /**
     * Forwards the escalated case to the external Helpdesk.
     *
     * @return the external ticket reference, or {@code null} when unconfigured or
     *         on a transient failure (caller degrades, never blocks).
     */
    String raiseTicket(SupportCase supportCase);
}
