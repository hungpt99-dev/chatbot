package com.helpdesk.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Inbound SOP payload. {@code id} is supplied by the caller (stable, human-readable).
 */
public record SopRequest(
        @NotBlank String id,
        @NotBlank String title,
        String description,
        String category,
        String problemDescription,
        List<String> symptoms,
        List<String> prerequisites,
        String expectedResult,
        String failureCondition,
        String escalationCondition,
        @NotNull @Valid List<StepRequest> steps
) {}
