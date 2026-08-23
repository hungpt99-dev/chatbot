package com.helpdesk.web.dto;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopStep;
import com.helpdesk.domain.model.SopStepBranch;
import com.helpdesk.domain.model.StepType;
import com.helpdesk.domain.model.TerminalKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read model returned by the SOP API. Includes the full step graph + branches so
 * clients (and later the execution engine) can drive the conversation deterministically.
 */
public record SopResponse(
        String id,
        String title,
        String description,
        String category,
        String problemDescription,
        List<String> symptoms,
        List<String> prerequisites,
        String expectedResult,
        String failureCondition,
        String escalationCondition,
        int version,
        Instant createdAt,
        Instant updatedAt,
        List<StepDto> steps
) {
    public record StepDto(
            String stepKey,
            int stepOrder,
            String instruction,
            StepType type,
            String defaultNext,
            boolean terminal,
            TerminalKind terminalKind,
            List<BranchDto> branches
    ) {}

    public record BranchDto(
            String branchKey,
            String conditionText,
            String gotoStepKey
    ) {}

    public static SopResponse from(Sop sop) {
        List<StepDto> stepDtos = sop.getSteps().stream()
                .map(s -> new StepDto(
                        s.getStepKey(),
                        s.getStepOrder(),
                        s.getInstruction(),
                        s.getType(),
                        s.getDefaultNext(),
                        s.isTerminal(),
                        s.getTerminalKind(),
                        s.getBranches().stream()
                                .map(b -> new BranchDto(b.getBranchKey(), b.getConditionText(), b.getGotoStepKey()))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
        return new SopResponse(
                sop.getCode(), sop.getTitle(), sop.getDescription(), sop.getCategory(),
                sop.getProblemDescription(), sop.getSymptoms(), sop.getPrerequisites(),
                sop.getExpectedResult(), sop.getFailureCondition(), sop.getEscalationCondition(),
                sop.getVersion(), sop.getCreatedAt(), sop.getUpdatedAt(), stepDtos
        );
    }

    /**
     * Human-readable rendering of a step: its instruction plus, if present, the
     * enumerable branch options. This is the text the assistant paraphrases; the
     * branches are presented so the user (and the interpreting layer) can pick a
     * valid branch key.
     */
    public String responseBody(StepDto step) {
        StringBuilder sb = new StringBuilder();
        sb.append(step.instruction());
        if (step.branches() != null && !step.branches().isEmpty()) {
            sb.append("\n(");
            List<String> opts = new ArrayList<>();
            for (BranchDto b : step.branches()) {
                opts.add(b.branchKey() + ": " + (b.conditionText() == null ? "" : b.conditionText()));
            }
            sb.append(String.join(" | ", opts));
            sb.append(")");
        }
        return sb.toString();
    }
}
