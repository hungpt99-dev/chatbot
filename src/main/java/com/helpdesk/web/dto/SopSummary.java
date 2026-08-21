package com.helpdesk.web.dto;

/**
 * Lightweight SOP summary for list views.
 */
public record SopSummary(
        String id,
        String title,
        String category,
        String description
) {}
