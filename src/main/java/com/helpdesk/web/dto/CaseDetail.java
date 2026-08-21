package com.helpdesk.web.dto;

import com.helpdesk.domain.model.SupportCase;

/** Full support case detail (GET /api/cases/{reference}). */
public record CaseDetail(
        String reference,
        Long conversationId,
        String employee,
        String problem,
        String sopId,
        String sopTitle,
        String status,
        String failedStepKey,
        String escalationReason,
        java.time.Instant startedAt,
        java.time.Instant resolvedAt,
        java.time.Instant escalatedAt
) {
    public static CaseDetail from(SupportCase c) {
        return new CaseDetail(
                c.getReference(), c.getConversationId(), c.getEmployee(), c.getProblem(),
                c.getSopId(), c.getSopTitle(), c.getStatus().name(), c.getFailedStepKey(),
                c.getEscalationReason(), c.getStartedAt(), c.getResolvedAt(), c.getEscalatedAt());
    }
}
