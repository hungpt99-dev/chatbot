package com.helpdesk.domain.model;

/**
 * Lifecycle status of a support case (mirrors the conversation's SOP execution status).
 * OPEN → IN_PROGRESS → RESOLVED | ESCALATED.
 */
public enum ConversationStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    ESCALATED
}
