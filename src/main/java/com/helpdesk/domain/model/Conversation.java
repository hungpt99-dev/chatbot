package com.helpdesk.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One employee support dialogue. Owns the deterministic SOP execution state
 * (the SOP graph stays in {@link Sop}; this tracks position within it) plus
 * the conversation thread and its derived {@link SupportCase}.
 */
@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id")
    private String hotelId;

    @Column(name = "sop_id")
    private String sopId;

    @Column(name = "current_step_key")
    private String currentStepKey;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConversationStatus status = ConversationStatus.OPEN;

    @Column(name = "employee", length = 256)
    private String employee;

    @Column(name = "problem_summary", columnDefinition = "TEXT")
    private String problemSummary;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "last_user_message", columnDefinition = "TEXT")
    private String lastUserMessage;

    @Column(name = "last_assistant_message", columnDefinition = "TEXT")
    private String lastAssistantMessage;

    @Column(name = "last_intent")
    private String lastIntent;

    @Column(name = "last_step_result")
    private String lastStepResult;

    public Conversation() {}

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.startedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getHotelId() { return hotelId; }
    public void setHotelId(String hotelId) { this.hotelId = hotelId; }
    public String getSopId() { return sopId; }
    public void setSopId(String sopId) { this.sopId = sopId; }
    public String getCurrentStepKey() { return currentStepKey; }
    public void setCurrentStepKey(String currentStepKey) { this.currentStepKey = currentStepKey; }
    public ConversationStatus getStatus() { return status; }
    public void setStatus(ConversationStatus status) { this.status = status; }
    public String getEmployee() { return employee; }
    public void setEmployee(String employee) { this.employee = employee; }
    public String getProblemSummary() { return problemSummary; }
    public void setProblemSummary(String problemSummary) { this.problemSummary = problemSummary; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; }
    public String getLastUserMessage() { return lastUserMessage; }
    public void setLastUserMessage(String lastUserMessage) { this.lastUserMessage = lastUserMessage; }
    public String getLastAssistantMessage() { return lastAssistantMessage; }
    public void setLastAssistantMessage(String lastAssistantMessage) { this.lastAssistantMessage = lastAssistantMessage; }
    public String getLastIntent() { return lastIntent; }
    public void setLastIntent(String lastIntent) { this.lastIntent = lastIntent; }
    public String getLastStepResult() { return lastStepResult; }
    public void setLastStepResult(String lastStepResult) { this.lastStepResult = lastStepResult; }
}
