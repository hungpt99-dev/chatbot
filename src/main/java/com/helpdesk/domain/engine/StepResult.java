package com.helpdesk.domain.engine;

/**
 * The SOP-driven conclusion an AI (or offline interpreter) reports for completing a
 * single step. This is the only "decision" the interpreting layer is allowed to make;
 * the execution engine validates it against the current step and never invents a
 * destination.
 */
public enum StepResult {
    CONTINUE,
    RESOLVE,
    ESCALATE
}
