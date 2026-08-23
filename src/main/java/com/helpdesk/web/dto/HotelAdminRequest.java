package com.helpdesk.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload for admin hotel CRUD. {@code id} is supplied by the caller
 * (stable, human-readable property key). {@code location} and {@code region}
 * are optional on create/update.
 */
public record HotelAdminRequest(
        String id,
        @NotBlank String name,
        String location,
        String region
) {}
