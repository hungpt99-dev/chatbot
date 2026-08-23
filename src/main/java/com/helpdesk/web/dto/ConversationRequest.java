package com.helpdesk.web.dto;

/**
 * Inbound request to start a conversation. hotelId scopes retrieval + ownership.
 * {@code lang} is the employee's preferred language (optional; when set, the
 * assistant's reply is localized via the translation port).
 */
public record ConversationRequest(
        String hotelId,
        String employee,
        String problem,
        String lang
) {
    /** Backward-compatible constructor (no language preference). */
    public ConversationRequest(String hotelId, String employee, String problem) {
        this(hotelId, employee, problem, null);
    }
}
