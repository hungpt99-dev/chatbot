package com.helpdesk.domain.model;

import jakarta.persistence.*;
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

    protected Sop() {
        // JPA
    }

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getProblemDescription() { return problemDescription; }
    public void setProblemDescription(String problemDescription) { this.problemDescription = problemDescription; }

    public List<String> getSymptoms() { return symptoms; }
    public void setSymptoms(List<String> symptoms) { this.symptoms = symptoms; }

    public List<String> getPrerequisites() { return prerequisites; }
    public void setPrerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; }

    public String getExpectedResult() { return expectedResult; }
    public void setExpectedResult(String expectedResult) { this.expectedResult = expectedResult; }

    public String getFailureCondition() { return failureCondition; }
    public void setFailureCondition(String failureCondition) { this.failureCondition = failureCondition; }

    public String getEscalationCondition() { return escalationCondition; }
    public void setEscalationCondition(String escalationCondition) { this.escalationCondition = escalationCondition; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<SopStep> getSteps() { return steps; }
    public void setSteps(List<SopStep> steps) { this.steps = steps; }
}
