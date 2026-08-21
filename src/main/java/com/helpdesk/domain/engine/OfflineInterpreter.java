package com.helpdesk.domain.engine;

import com.helpdesk.web.dto.SopResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic (no-LLM) interpreter used when no AI provider is configured
 * (off-mode) or as a guardrail fallback. It derives a structured step outcome
 * from free-form text using cheap heuristics driven entirely by the SOP graph:
 *
 * <ul>
 *   <li>If the current step exposes branches, the first branch whose condition
 *       text is CONTAINED in the user message (or whose key is mentioned) is
 *       chosen; otherwise the AI is asked to disambiguate and a CONTINUE with the
 *       default branch is returned.</li>
 *   <li>If any "escalate"/"không được"/"không thể"/"still not"/"bỏ cuộc" token is
 *       present, the outcome is ESCALATE.</li>
 *   <li>If a "resolved"/"được rồi"/"xong"/"done"/"fixed" token is present, the
 *       outcome is RESOLVE.</li>
 *   <li>Otherwise CONTINUE.</li>
 * </ul>
 *
 * The outcome is intentionally conservative: it never invents a destination step
 * — routing is always performed by {@link SopExecutionEngine}.
 */
@Component
public class OfflineInterpreter {

    private static final List<String> ESCALATE_TOKENS = List.of(
            "escalate", "không được", "không the", "không thể", "bỏ cuộc",
            "still not", "still broken", "not working", "can't", "cannot", "give up");
    private static final List<String> RESOLVE_TOKENS = List.of(
            "resolved", "được rồi", "xong", "done", "fixed", "hoạt động", "ok", "ổn");

    public StepOutcome interpret(String userMessage, SopResponse.StepDto current) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase();

        if (containsAny(msg, ESCALATE_TOKENS)) {
            return StepOutcome.of(null, "ESCALATE");
        }
        if (containsAny(msg, RESOLVE_TOKENS)) {
            return StepOutcome.of(null, "RESOLVE");
        }
        if (current != null && current.branches() != null && !current.branches().isEmpty()) {
            Optional<SopResponse.BranchDto> hit = current.branches().stream()
                    .filter(b -> {
                        String cond = b.conditionText() == null ? "" : b.conditionText().toLowerCase();
                        String key = b.branchKey() == null ? "" : b.branchKey().toLowerCase();
                        return (!cond.isEmpty() && msg.contains(cond))
                                || (!key.isEmpty() && msg.contains(key));
                    })
                    .findFirst();
            if (hit.isPresent()) {
                return StepOutcome.of(hit.get().branchKey(), "CONTINUE");
            }
        }
        // No decisive signal: continue along the default path.
        return StepOutcome.of(null, "CONTINUE");
    }

    private boolean containsAny(String haystack, List<String> needles) {
        for (String n : needles) {
            if (!n.isEmpty() && haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
