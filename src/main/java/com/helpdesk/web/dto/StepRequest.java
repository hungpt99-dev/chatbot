package com.helpdesk.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StepRequest(
        @NotBlank String stepKey,
        int stepOrder,
        String instruction,
        @NotNull SopStepType type,
        String defaultNext,
        boolean terminal,
        SopTerminalKind terminalKind,
        List<BranchRequest> branches
) {}
