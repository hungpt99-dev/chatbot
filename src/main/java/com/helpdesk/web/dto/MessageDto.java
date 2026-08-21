package com.helpdesk.web.dto;

import com.helpdesk.domain.model.MessageKind;
import com.helpdesk.domain.model.MessageRole;

import java.time.Instant;

/** One message in a conversation thread (read model). */
public record MessageDto(
        int seq,
        MessageRole role,
        MessageKind kind,
        String content,
        String stepKey,
        Instant createdAt
) {}
