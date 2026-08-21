package com.helpdesk.web.dto;

import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.domain.model.SupportCase;

import java.time.Instant;

/** Summary row for the support case board (GET /api/cases). */
public record CaseSummary(
        String reference,
        Long conversationId,
        String employee,
        String problem,
        String sopId,
        String sopTitle,
        ConversationStatus status,
        String failedStepKey,
        Instant startedAt,
        Instant resolvedAt,
        Instant escalatedAt
) {
    public static CaseSummary from(SupportCase c) {
        return new CaseSummary(
                c.getReference(), c.getConversationId(), c.getEmployee(), c.getProblem(),
                c.getSopId(), c.getSopTitle(), c.getStatus(), c.getFailedStepKey(),
                c.getStartedAt(), c.getResolvedAt(), c.getEscalatedAt());
    }
}
