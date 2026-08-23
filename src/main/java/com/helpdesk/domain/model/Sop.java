package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A Standard Operating Procedure. The SOP is the single source of truth for what
 * the AI may tell an employee to do. Steps form a directed graph: a linear path via
 * {@code defaultNext} plus optional conditional {@link SopStepBranch}es.
 */
@Entity
@Table(name = "sop")
@Getter
@Setter
@NoArgsConstructor
public class Sop {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    /** Human-readable SOP code (e.g. "printer-cannot-print"), unique within a hotel. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String problemDescription;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> symptoms = new ArrayList<>();

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> prerequisites = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String expectedResult;

    @Column(columnDefinition = "TEXT")
    private String failureCondition;

    @Column(columnDefinition = "TEXT")
    private String escalationCondition;

    private int version = 1;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "sop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stepOrder ASC")
    private List<SopStep> steps = new ArrayList<>();

    public Sop(String id, String title) {
        this.id = id;
        this.title = title;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
