package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * A property in the hotel chain. SOPs, conversations, cases, and audit events are
 * all scoped to a hotel (multi-tenant, ADR-0008). Each hotel shares the corporate
 * SOP set (replicated on seed) but may differ in content and may add hotel-specific
 * SOPs, so every tenant owns its own SOP instances.
 */
@Entity
@Table(name = "hotel")
@Getter
@Setter
@NoArgsConstructor
public class Hotel {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "location", length = 256)
    private String location;

    @Column(name = "region", length = 256)
    private String region;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Hotel(String id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.createdAt = Instant.now();
    }
}
