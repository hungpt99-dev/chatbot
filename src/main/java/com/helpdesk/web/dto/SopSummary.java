package com.helpdesk.web.dto;

/**
 * Lightweight SOP summary for list views. {@code code} is the SOP business key
 * (unique within a hotel); {@code id} in the SOP entity is the composite
 * hotelId:code key.
 */
public record SopSummary(
        String code,
        String title,
        String category,
        String description
) {}
