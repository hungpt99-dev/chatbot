package com.helpdesk.domain.engine;

import com.helpdesk.domain.model.StepType;
import com.helpdesk.web.dto.SopResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The offline interpreter must derive a structured outcome from free text using
 * only the SOP graph — it never invents a destination step.
 */
class OfflineInterpreterTest {

    private final OfflineInterpreter interpreter = new OfflineInterpreter();

    private SopResponse.StepDto branchStep() {
        return new SopResponse.StepDto("1", 1, "What is the status?",
                StepType.QUESTION, "2", false, null,
                List.of(
                        new SopResponse.BranchDto("offline", "mất mạng", "2"),
                        new SopResponse.BranchDto("online", "có mạng", "3")));
    }

    @Test
    void escalapeTokensYieldEscalate() {
        StepOutcome o = interpreter.interpret("vẫn không được, bỏ cuộc", branchStep());
        assertTrue(o.isEscalate());
    }

    @Test
    void resolveTokensYieldResolve() {
        StepOutcome o = interpreter.interpret("xong rồi, cảm ơn", branchStep());
        assertTrue(o.isResolve());
    }

    @Test
    void branchConditionMatchPicksBranchKey() {
        StepOutcome o = interpreter.interpret("mất mạng rồi", branchStep());
        assertEquals("offline", o.branchKey());
        assertTrue(o.isContinue());
    }

    @Test
    void branchKeyMentionedPicksBranch() {
        StepOutcome o = interpreter.interpret("online", branchStep());
        assertEquals("online", o.branchKey());
    }

    @Test
    void noSignalContinues() {
        StepOutcome o = interpreter.interpret("tôi đang kiểm tra", branchStep());
        assertTrue(o.isContinue());
        assertFalse(o.hasBranch());
    }
}
