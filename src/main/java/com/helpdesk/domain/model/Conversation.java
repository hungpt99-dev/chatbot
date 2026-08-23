package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * One employee support dialogue. Owns the deterministic SOP execution state
 * (the SOP graph stays in {@link Sop}; this tracks position within it) plus
 * the conversation thread and its derived {@link SupportCase}.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor
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
}
