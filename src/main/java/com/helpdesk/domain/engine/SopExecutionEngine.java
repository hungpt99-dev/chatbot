package com.helpdesk.domain.engine;

import com.helpdesk.domain.model.ConversationStatus;
import com.helpdesk.web.dto.SopResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The deterministic SOP execution engine. This is the layer the AI must NOT
 * replace: it owns the state machine (current step, legal next steps, status,
 * escalation) and validates every AI decision before it mutates state.
 *
 * <p>Routing rules:
 * <ul>
 *   <li>A step with {@code terminal=true} and {@code terminalKind=RESOLVE} ends the
 *       flow as RESOLVED.</li>
 *   <li>A step with {@code terminal=true} and {@code terminalKind=ESCALATE}, or any
 *       step whose outcome is ESCALATE, ends the flow as ESCALATED.</li>
 *   <li>Otherwise: if the AI picked a branch whose key exists on the current step,
 *       route to that branch's {@code gotoStepKey}; else route to {@code defaultNext}.</li>
 *   <li>If neither a valid branch nor a {@code defaultNext} exists, the flow terminates
 *       as ESCALATED (guardrail: never invent a destination).</li>
 * </ul>
 *
 * <p>Guardrails enforced here (no exceptions are "helpful" — invalid transitions
 * are refused rather than silently forwarded):
 * <ul>
 *   <li>The AI may only choose a branch key that is enumerated on the current step.</li>
 *   <li>A chosen branch must resolve to a step that actually exists in the SOP.</li>
 *   <li>Step results are restricted to CONTINUE / RESOLVE / ESCALATE.</li>
 * </ul>
 */
@Component
public class SopExecutionEngine {

    /** Advances from the given current step given an AI outcome. Pure (no persistence). */
    public EngineResult advance(SopResponse sop, SopResponse.StepDto current, StepOutcome outcome) {
        List<EngineResult.ExecutedStep> executed = new ArrayList<>();
        if (current != null) {
            executed.add(new EngineResult.ExecutedStep(
                    current.stepKey(), current.instruction(), outcome.stepResult()));
        }

        // 1) Terminal step reached via previous routing, or explicit RESOLVE/ESCALATE.
        if (outcome.isResolve()
                || (current != null && current.terminal() && current.terminalKind() == com.helpdesk.domain.model.TerminalKind.RESOLVE)) {
            return finish(current, ConversationStatus.RESOLVED, executed);
        }
        if (outcome.isEscalate()
                || (current != null && current.terminal() && current.terminalKind() == com.helpdesk.domain.model.TerminalKind.ESCALATE)) {
            return finish(current, ConversationStatus.ESCALATED, executed);
        }

        // 2) Determine the next step key.
        String nextKey = resolveNextKey(current, outcome);
        if (nextKey == null) {
            // No valid path forward — guardrail: escalate rather than invent.
            return finish(current, ConversationStatus.ESCALATED, executed);
        }

        SopResponse.StepDto next = findStep(sop, nextKey);
        if (next == null) {
            // Branch/goto pointed at a non-existent step — refuse, escalate.
            return finish(current, ConversationStatus.ESCALATED, executed);
        }

        return new EngineResult(next, next.stepKey(), ConversationStatus.IN_PROGRESS.name(),
                executed, false);
    }

    private String resolveNextKey(SopResponse.StepDto current, StepOutcome outcome) {
        if (current == null) {
            return null;
        }
        if (outcome.hasBranch()) {
            // Must be an enumerated branch on the current step.
            Optional<SopResponse.BranchDto> b = (current.branches() == null ? List.<SopResponse.BranchDto>of()
                    : current.branches()).stream()
                    .filter(x -> x.branchKey().equalsIgnoreCase(outcome.branchKey()))
                    .findFirst();
            if (b.isPresent()) {
                return b.get().gotoStepKey();
            }
            // Invalid branch key -> do not route; fall through to defaultNext (safer).
        }
        return current.defaultNext();
    }

    private EngineResult finish(SopResponse.StepDto current,
                                ConversationStatus status,
                                List<EngineResult.ExecutedStep> executed) {
        String key = current == null ? null : current.stepKey();
        return new EngineResult(current, key, status.name(), executed, true);
    }

    private SopResponse.StepDto findStep(SopResponse sop, String key) {
        if (key == null) return null;
        return sop.steps().stream()
                .filter(s -> s.stepKey().equals(key))
                .findFirst()
                .orElse(null);
    }
}
