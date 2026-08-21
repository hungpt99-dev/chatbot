package com.helpdesk.web.dto;

import com.helpdesk.domain.model.Conversation;
import com.helpdesk.domain.model.ConversationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Read model for a conversation, including its SOP execution state and full thread.
 */
public record ConversationResponse(
        Long id,
        String sopId,
        String currentStepKey,
        ConversationStatus status,
        String employee,
        String problem,
        Instant startedAt,
        Instant resolvedAt,
        Instant escalatedAt,
        List<MessageDto> messages
) {
    public static ConversationResponse from(Conversation c,
                                            SopResponse sop,
                                            List<MessageDto> messages) {
        return new ConversationResponse(
                c.getId(), c.getSopId(), c.getCurrentStepKey(), c.getStatus(),
                c.getEmployee(), c.getProblemSummary(), c.getStartedAt(),
                c.getResolvedAt(), c.getEscalatedAt(), messages);
    }
}
