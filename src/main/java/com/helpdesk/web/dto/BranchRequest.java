package com.helpdesk.web.dto;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(
        @NotBlank String branchKey,
        String conditionText,
        @NotBlank String gotoStepKey
) {}
