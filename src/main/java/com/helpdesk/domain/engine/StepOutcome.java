package com.helpdesk.domain.engine;

/**
 * The outcome the AI reports for completing a single SOP step. This is the only
 * decision the AI layer is allowed to make per step. The engine validates it
 * against the current step and NEVER invents new steps or destinations.
 *
 * <p>branchKey is the chosen branch (one of the current step's enumerated
 * branchKeys, or null when none applies); resolved/answer are free-form text
 * recorded for the audit trail. stepResult maps to a SOP-driven conclusion:
 * CONTINUE, RESOLVE, or ESCALATE.
 */
public record StepOutcome(
        String branchKey,
        String stepResult,   // CONTINUE | RESOLVE | ESCALATE
        String resolved,     // free-form resolution note (when RESOLVE)
        String answer,       // free-form user-answer / narrative (recorded)
        String escalationReason // why escalation is needed (when ESCALATE)
) {

    public static StepOutcome of(String branchKey, String stepResult) {
        return new StepOutcome(branchKey, stepResult, null, null, null);
    }

    public boolean hasBranch() {
        return branchKey != null && !branchKey.isBlank();
    }

    public boolean isEscalate() {
        return "ESCALATE".equalsIgnoreCase(stepResult);
    }

    public boolean isResolve() {
        return "RESOLVE".equalsIgnoreCase(stepResult);
    }

    public boolean isContinue() {
        return "CONTINUE".equalsIgnoreCase(stepResult);
    }
}
