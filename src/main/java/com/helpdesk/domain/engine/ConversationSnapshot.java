package com.helpdesk.domain.engine;

import com.helpdesk.web.dto.SopResponse;

import java.util.List;

/**
 * Read-only context handed to the LLM so it can make a step decision. Contains
 * just enough to interpret the employee's latest message against the SOP graph —
 * the SOP id/title, the current step (with its enumerated branch options), and the
 * recent message thread. The LLM returns a {@link LlmStepDecision}; it does NOT
 * receive the authority to mutate SOP state.
 */
public record ConversationSnapshot(
        String conversationId,
        String sopId,
        String sopTitle,
        SopResponse.StepDto currentStep,
        List<String> recentMessages   // prior turns, oldest-first, for context
) {}
