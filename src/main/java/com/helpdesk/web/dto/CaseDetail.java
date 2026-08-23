package com.helpdesk.web.dto;

import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.model.SupportCase;

import java.time.Instant;

/** Full support case detail (GET /api/cases/{reference}). */
public record CaseDetail(
        String reference,
        Long conversationId,
        String hotelId,
        String employee,
        String problem,
        String sopId,
        String sopTitle,
        ConversationStatus status,
        String failedStepKey,
        String escalationReason,
        String externalTicketRef,
        Instant startedAt,
        Instant resolvedAt,
        Instant escalatedAt
) {
    public static CaseDetail from(SupportCase c) {
        return new CaseDetail(
                c.getReference(), c.getConversationId(), c.getHotelId(), c.getEmployee(), c.getProblem(),
                c.getSopId(), c.getSopTitle(), c.getStatus(), c.getFailedStepKey(),
                c.getEscalationReason(), c.getExternalTicketRef(),
                c.getStartedAt(), c.getResolvedAt(), c.getEscalatedAt());
    }
}
