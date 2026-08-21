package com.helpdesk.domain.model;

/**
 * SOP step taxonomy. The AI paraphrases/translates the step's {@code instruction};
 * the {@code type} tells the execution engine what kind of interaction this is.
 */
public enum StepType {
    /** Ask the user a diagnostic question and wait for a reply. */
    QUESTION,
    /** Instruct the user to perform an action. */
    ACTION,
    /** Ask the user to verify an expected condition. */
    CHECK,
    /** Terminal step: escalate to IT Support. */
    ESCALATE
}
