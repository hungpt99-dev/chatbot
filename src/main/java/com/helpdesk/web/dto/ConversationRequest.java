package com.helpdesk.web.dto;

/** Inbound request to start a conversation. hotelId scopes retrieval + ownership. */
public record ConversationRequest(
        String hotelId,
        String employee,
        String problem
) {}
