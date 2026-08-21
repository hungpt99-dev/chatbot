package com.helpdesk.domain.engine;

import com.helpdesk.web.dto.SopResponse;

import java.util.List;

/**
 * Result of advancing the SOP. Carries the (possibly moved) current step, the
 * revised status, and the ordered history of executed steps — everything the
 * API/UI needs to render state and everything the audit layer needs to record.
 */
public record EngineResult(
        SopResponse.StepDto currentStep,
        String currentStepKey,
        String status,                 // ConversationStatus name
        List<ExecutedStep> executed,
        boolean conversationOver       // true when status is RESOLVED or ESCALATED
) {

    public record ExecutedStep(String stepKey, String instruction, String stepResult) {}
}
