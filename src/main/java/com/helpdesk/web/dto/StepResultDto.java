package com.helpdesk.web.dto;

import com.helpdesk.domain.engine.StepResult;

/**
 * Structured step result the AI reports. The app validates this before updating
 * execution state; invalid output never mutates SOP state. {@code result} is the
 * SOP-driven conclusion (CONTINUE / RESOLVE / ESCALATE) and is a typed enum, not a
 * free-form string.
 *
 * <p>{@code sopId}/{@code currentStep} are advisory context the caller may include; the
 * service always re-derives the real SOP/step from the conversation, so they are not
 * trusted as state.
 */
public record StepResultDto(
        StepResult result,   // CONTINUE | RESOLVE | ESCALATE (typed)
        String sopId,
        String currentStep,  // step key
        String resolved,     // free-form, when RESOLVE
        String escalationReason // free-form, when ESCALATE
) {}
