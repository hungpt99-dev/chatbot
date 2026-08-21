package com.helpdesk.web.dto;

import java.time.Instant;

/** One message in a conversation thread (read model). */
public record MessageDto(
        int seq,
        String role,
        String kind,
        String content,
        String stepKey,
        Instant createdAt
) {}
