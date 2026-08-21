package com.helpdesk.domain.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * One node in a SOP's execution graph. Ordered by {@code stepOrder}; the engine
 * advances via {@code defaultNext} (step key) or a matched {@link SopStepBranch}.
 */
@Entity
@Table(name = "sop_step")
public class SopStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sop_id")
    private Sop sop;

    @Column(name = "step_key", nullable = false)
    private String stepKey;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(columnDefinition = "TEXT")
    private String instruction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepType type;

    @Column(name = "default_next")
    private String defaultNext;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminal_kind")
    private TerminalKind terminalKind;

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SopStepBranch> branches = new ArrayList<>();

    protected SopStep() {
        // JPA
    }

    public Long getId() { return id; }

    public Sop getSop() { return sop; }
    public void setSop(Sop sop) { this.sop = sop; }

    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }

    public int getStepOrder() { return stepOrder; }
    public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public StepType getType() { return type; }
    public void setType(StepType type) { this.type = type; }

    public String getDefaultNext() { return defaultNext; }
    public void setDefaultNext(String defaultNext) { this.defaultNext = defaultNext; }

    public boolean isTerminal() { return terminal; }
    public void setTerminal(boolean terminal) { this.terminal = terminal; }

    public TerminalKind getTerminalKind() { return terminalKind; }
    public void setTerminalKind(TerminalKind terminalKind) { this.terminalKind = terminalKind; }

    public List<SopStepBranch> getBranches() { return branches; }
    public void setBranches(List<SopStepBranch> branches) { this.branches = branches; }
}
