package com.helpdesk.domain.model;

import jakarta.persistence.*;

/**
 * A conditional edge out of a {@link SopStep}. The LLM may only choose among the
 * enumerated {@code branchKey}s of the current step; it can never name an arbitrary
 * step. The engine routes to {@code gotoStepKey} when the branch is selected.
 */
@Entity
@Table(name = "sop_step_branch")
public class SopStepBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id")
    private SopStep step;

    @Column(name = "branch_key", nullable = false)
    private String branchKey;

    @Column(name = "condition_text")
    private String conditionText;

    @Column(name = "goto_step_key", nullable = false)
    private String gotoStepKey;

    protected SopStepBranch() {
        // JPA
    }

    public Long getId() { return id; }

    public SopStep getStep() { return step; }
    public void setStep(SopStep step) { this.step = step; }

    public String getBranchKey() { return branchKey; }
    public void setBranchKey(String branchKey) { this.branchKey = branchKey; }

    public String getConditionText() { return conditionText; }
    public void setConditionText(String conditionText) { this.conditionText = conditionText; }

    public String getGotoStepKey() { return gotoStepKey; }
    public void setGotoStepKey(String gotoStepKey) { this.gotoStepKey = gotoStepKey; }
}
