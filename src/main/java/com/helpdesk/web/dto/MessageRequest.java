package com.helpdesk.web.dto;

/**
 * Inbound user message for an active conversation. In Phase 1B the structured
 * step outcome is optional (the offline interpreter derives it); in Phase 1C the
 * LLM supplies {@code stepResult}/{@code branchKey} and the app validates it
 * before mutating SOP state (guardrail: never mutate on invalid structured output).
 */
public record MessageRequest(
        String message,
        String branchKey,
        StepResultDto stepResult
) {}
