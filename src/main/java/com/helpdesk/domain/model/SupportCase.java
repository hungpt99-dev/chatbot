package com.helpdesk.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The support case derived from a {@link Conversation}. A conversation that
 * resolves or escalates produces a case in the corresponding status. The case
 * is the unit tracked on the support board (GET /api/cases).
 */
@Entity
@Table(name = "support_case")
public class SupportCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", unique = true, nullable = false, length = 64)
    private String reference;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "employee", length = 256)
    private String employee;

    @Column(name = "problem", columnDefinition = "TEXT")
    private String problem;

    @Column(name = "sop_id")
    private String sopId;

    @Column(name = "sop_title")
    private String sopTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConversationStatus status;

    @Column(name = "failed_step_key")
    private String failedStepKey;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    public SupportCase() {}

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getEmployee() { return employee; }
    public void setEmployee(String employee) { this.employee = employee; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getSopId() { return sopId; }
    public void setSopId(String sopId) { this.sopId = sopId; }
    public String getSopTitle() { return sopTitle; }
    public void setSopTitle(String sopTitle) { this.sopTitle = sopTitle; }
    public ConversationStatus getStatus() { return status; }
    public void setStatus(ConversationStatus status) { this.status = status; }
    public String getFailedStepKey() { return failedStepKey; }
    public void setFailedStepKey(String failedStepKey) { this.failedStepKey = failedStepKey; }
    public String getEscalationReason() { this.escalationReason = escalationReason; return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; }
}
