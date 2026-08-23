package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A conditional edge out of a {@link SopStep}. The LLM may only choose among the
 * enumerated {@code branchKey}s of the current step; it can never name an arbitrary
 * step. The engine routes to {@code gotoStepKey} when the branch is selected.
 */
@Entity
@Table(name = "sop_step_branch")
@Getter
@Setter
@NoArgsConstructor
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
}
