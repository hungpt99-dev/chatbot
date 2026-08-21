package com.helpdesk.domain.engine;

import com.helpdesk.domain.model.StepType;
import com.helpdesk.domain.model.TerminalKind;
import com.helpdesk.web.dto.SopResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic engine behaviour — no DB, no LLM. Verifies branching, resolution,
 * escalation, and the guardrails that prevent the AI from inventing steps.
 */
class SopExecutionEngineTest {

    private final SopExecutionEngine engine = new SopExecutionEngine();

    // SOP graph:
    //  step1 (QUESTION, branch offline->step2 / online->step3, default->step2)
    //  step2 (ACTION, default->step3)
    //  step3 (CHECK, terminal RESOLVE)
    //  step9 (ESCALATE, terminal ESCALATE)
    private SopResponse sop() {
        SopResponse.StepDto step1 = new SopResponse.StepDto("1", 1, "Powered on?",
                StepType.QUESTION, "2", false, null,
                List.of(
                        new SopResponse.BranchDto("offline", "printer is off", "2"),
                        new SopResponse.BranchDto("online", "printer is on", "3")));
        SopResponse.StepDto step2 = new SopResponse.StepDto("2", 2, "Check cable",
                StepType.ACTION, "3", false, null, List.of());
        SopResponse.StepDto step3 = new SopResponse.StepDto("3", 3, "Test print",
                StepType.CHECK, null, true, TerminalKind.RESOLVE, List.of());
        SopResponse.StepDto step9 = new SopResponse.StepDto("9", 9, "Escalate to IT",
                StepType.ESCALATE, null, true, TerminalKind.ESCALATE, List.of());
        return new SopResponse("printer", "Printer", null, null, null, null, null, null,
                null, null, 1, null, null, List.of(step1, step2, step3, step9));
    }

    @Test
    void defaultNextUsedWhenNoBranch() {
        EngineResult r = engine.advance(sop(), step("1"), StepOutcome.of(null, "CONTINUE"));
        assertEquals("2", r.currentStepKey());
        assertEquals("IN_PROGRESS", r.status());
        assertFalse(r.conversationOver());
    }

    @Test
    void validBranchRoutesToGoto() {
        EngineResult r = engine.advance(sop(), step("1"), StepOutcome.of("online", "CONTINUE"));
        assertEquals("3", r.currentStepKey());
    }

    @Test
    void invalidBranchFallsBackToDefaultNext() {
        // "sideways" is not an enumerated branch -> engine must NOT route to it.
        EngineResult r = engine.advance(sop(), step("1"), StepOutcome.of("sideways", "CONTINUE"));
        assertEquals("2", r.currentStepKey());
    }

    @Test
    void reachingResolveTerminalEndsResolved() {
        EngineResult r = engine.advance(sop(), step("3"), StepOutcome.of(null, "CONTINUE"));
        assertEquals("RESOLVED", r.status());
        assertTrue(r.conversationOver());
    }

    @Test
    void explicitResolveEndsResolved() {
        EngineResult r = engine.advance(sop(), step("2"), StepOutcome.of(null, "RESOLVE"));
        assertEquals("RESOLVED", r.status());
    }

    @Test
    void explicitEscalateEndsEscalated() {
        EngineResult r = engine.advance(sop(), step("2"), StepOutcome.of(null, "ESCALATE"));
        assertEquals("ESCALATED", r.status());
        assertTrue(r.conversationOver());
    }

    @Test
    void reachingEscalateTerminalEndsEscalated() {
        EngineResult r = engine.advance(sop(), step("9"), StepOutcome.of(null, "CONTINUE"));
        assertEquals("ESCALATED", r.status());
    }

    @Test
    void noValidPathEscalatesRatherThanInvents() {
        // step2 defaultNext="3" but imagine a step whose defaultNext points nowhere.
        SopResponse.StepDto dead = new SopResponse.StepDto("x", 1, "Orphan",
                StepType.ACTION, "does-not-exist", false, null, List.of());
        SopResponse s = new SopResponse("s", "S", null, null, null, null, null, null,
                null, null, 1, null, null, List.of(dead));
        EngineResult r = engine.advance(s, dead, StepOutcome.of(null, "CONTINUE"));
        assertEquals("ESCALATED", r.status());
    }

    @Test
    void executedHistoryRecorded() {
        EngineResult r = engine.advance(sop(), step("1"), StepOutcome.of("online", "CONTINUE"));
        assertEquals(1, r.executed().size());
        assertEquals("1", r.executed().get(0).stepKey());
        assertEquals("CONTINUE", r.executed().get(0).stepResult());
    }

    private SopResponse.StepDto step(String key) {
        return sop().steps().stream().filter(s -> s.stepKey().equals(key)).findFirst().orElseThrow();
    }
}
