package com.helpdesk.domain.engine;

/**
 * Structured decision an LLM returns for the current SOP step. This is the
 * AI's *proposal* — the app validates it (branch key must exist on the current
 * step; intent must be one of CONTINUE/RESOLVE/ESCALATE) before the deterministic
 * {@link SopExecutionEngine} applies it. The LLM never decides the destination step.
 */
public record LlmStepDecision(
        String intent,          // CONTINUE | RESOLVE | ESCALATE (optional; inferred if blank)
        String branchKey,        // chosen enumerated branch key, or null
        String stepResult,      // free-form note when RESOLVE
        String escalationReason,// free-form reason when ESCALATE
        String response         // assistant-facing natural-language reply (optional)
) {
    public static LlmStepDecision of(String intent, String branchKey, String response) {
        return new LlmStepDecision(intent, branchKey, null, null, response);
    }

    public boolean hasBranch() {
        return branchKey != null && !branchKey.isBlank();
    }

    public boolean isEscalate() {
        return "ESCALATE".equalsIgnoreCase(intent);
    }

    public boolean isResolve() {
        return "RESOLVE".equalsIgnoreCase(intent);
    }
}
