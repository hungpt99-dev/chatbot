package com.helpdesk.domain.model;

import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.StepRequest;
import com.helpdesk.web.dto.BranchRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the inbound {@link SopRequest} DTO and the persistent
 * {@link Sop}/{@link SopStep}/{@link SopStepBranch} entities. Keeps JPA entities
 * free of web concerns.
 */
public final class SopAssembler {

    private SopAssembler() {}

    public static Sop toEntity(SopRequest req) {
        Sop sop = new Sop(req.id(), req.title());
        applyScalars(req, sop);
        sop.setSteps(buildSteps(req, sop));
        return sop;
    }

    /**
     * Merges an inbound request onto an EXISTING managed entity (used by update).
     * Steps are cleared and replaced so orphan removal cleans up the old graph,
     * avoiding duplicate-key errors on the (id, step_key) unique constraint.
     */
    public static void apply(SopRequest req, Sop existing) {
        applyScalars(req, existing);
        existing.getSteps().clear();
        existing.getSteps().addAll(buildSteps(req, existing));
    }

    private static void applyScalars(SopRequest req, Sop sop) {
        sop.setTitle(req.title());
        sop.setDescription(req.description());
        sop.setCategory(req.category());
        sop.setProblemDescription(req.problemDescription());
        sop.setSymptoms(nullToEmpty(req.symptoms()));
        sop.setPrerequisites(nullToEmpty(req.prerequisites()));
        sop.setExpectedResult(req.expectedResult());
        sop.setFailureCondition(req.failureCondition());
        sop.setEscalationCondition(req.escalationCondition());
    }

    private static List<SopStep> buildSteps(SopRequest req, Sop owner) {
        List<SopStep> steps = new ArrayList<>();
        int order = 1;
        for (StepRequest sr : req.steps()) {
            SopStep step = new SopStep();
            step.setSop(owner);
            step.setStepKey(sr.stepKey());
            step.setStepOrder(sr.stepOrder() != 0 ? sr.stepOrder() : order++);
            step.setInstruction(sr.instruction());
            step.setType(StepType.valueOf(sr.type().name()));
            step.setDefaultNext(sr.defaultNext());
            step.setTerminal(sr.terminal());
            if (sr.terminalKind() != null) {
                step.setTerminalKind(TerminalKind.valueOf(sr.terminalKind().name()));
            }
            List<SopStepBranch> branches = new ArrayList<>();
            if (sr.branches() != null) {
                for (BranchRequest br : sr.branches()) {
                    SopStepBranch b = new SopStepBranch();
                    b.setStep(step);
                    b.setBranchKey(br.branchKey());
                    b.setConditionText(br.conditionText());
                    b.setGotoStepKey(br.gotoStepKey());
                    branches.add(b);
                }
            }
            step.setBranches(branches);
            steps.add(step);
        }
        return steps;
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
