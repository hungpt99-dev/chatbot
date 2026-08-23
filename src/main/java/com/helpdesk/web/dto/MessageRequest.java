package com.helpdesk.web.dto;

/**
 * Inbound user message for an active conversation. In Phase 1B the structured
 * step outcome is optional (the offline interpreter derives it); in Phase 1C the
 * LLM supplies {@code stepResult}/{@code branchKey} and the app validates it
 * before mutating SOP state (guardrail: never mutate on invalid structured output).
 * {@code lang} is the employee's preferred language (optional; when set, the
 * assistant's reply is localized via the translation port).
 */
public record MessageRequest(
        String message,
        String branchKey,
        StepResultDto stepResult,
        String lang
) {
    /** Backward-compatible constructor (no language preference). */
    public MessageRequest(String message, String branchKey, StepResultDto stepResult) {
        this(message, branchKey, stepResult, null);
    }
}
