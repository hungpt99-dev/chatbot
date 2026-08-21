package com.helpdesk.web.dto;

/**
 * Inbound request to start a conversation. The problem text drives SOP retrieval;
 * the engine then begins at the SOP's first step.
 */
public record ConversationRequest(
        String employee,
        String problem
) {}
