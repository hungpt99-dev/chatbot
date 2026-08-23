package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * Append-only audit log. Every consequential action (retrieval, step shown,
 * branch taken, step result, escalation, resolution) is recorded here so the
 * system can always answer "why did the AI tell the user to do this?" with a
 * trail back to a specific SOP step.
 */
@Entity
@Table(name = "audit_event")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "hotel_id")
    private String hotelId;

    @Column(name = "sop_id")
    private String sopId;

    @Column(name = "step_key")
    private String stepKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public AuditEvent(Long conversationId, String sopId, String stepKey,
                      String eventType, String detail) {
        this.conversationId = conversationId;
        this.sopId = sopId;
        this.stepKey = stepKey;
        this.eventType = eventType;
        this.detail = detail;
    }

    public AuditEvent(String hotelId, Long conversationId, String sopId, String stepKey,
                      String eventType, String detail) {
        this.hotelId = hotelId;
        this.conversationId = conversationId;
        this.sopId = sopId;
        this.stepKey = stepKey;
        this.eventType = eventType;
        this.detail = detail;
    }
}
