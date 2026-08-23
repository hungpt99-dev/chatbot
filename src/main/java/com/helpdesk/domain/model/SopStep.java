package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

/**
 * One node in a SOP's execution graph. Ordered by {@code stepOrder}; the engine
 * advances via {@code defaultNext} (step key) or a matched {@link SopStepBranch}.
 */
@Entity
@Table(name = "sop_step")
@Getter
@Setter
@NoArgsConstructor
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
}
