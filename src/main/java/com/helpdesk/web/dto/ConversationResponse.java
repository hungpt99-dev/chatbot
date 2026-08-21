package com.helpdesk.web.dto;

import com.helpdesk.domain.model.Conversation;

/**
 * Read model for a conversation, including its SOP execution state and full thread.
 */
public record ConversationResponse(
        Long id,
        String sopId,
        String currentStepKey,
        String status,
        String employee,
        String problem,
        java.time.Instant startedAt,
        java.time.Instant resolvedAt,
        java.time.Instant escalatedAt,
        java.util.List<MessageDto> messages
) {
    public static ConversationResponse from(Conversation c,
                                            com.helpdesk.web.dto.SopResponse sop,
                                            java.util.List<MessageDto> messages) {
        return new ConversationResponse(
                c.getId(), c.getSopId(), c.getCurrentStepKey(), c.getStatus().name(),
                c.getEmployee(), c.getProblemSummary(), c.getStartedAt(),
                c.getResolvedAt(), c.getEscalatedAt(), messages);
    }
}
