package com.helpdesk.web.dto;

/**
 * Structured step result the AI reports. Mirrors the proposal's concept:
 * { intent, sopId, currentStep, stepResult, response }. The app validates this
 * before updating execution state; invalid output never mutates SOP state.
 */
public record StepResultDto(
        String intent,        // TROUBLESHOOT
        String sopId,
        String currentStep,   // step key
        String result,        // CONTINUE | RESOLVE | ESCALATE
        String resolved,      // free-form, when RESOLVE
        String escalationReason // free-form, when ESCALATE
) {}
