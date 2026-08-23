package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * The support case derived from a {@link Conversation}. A conversation that
 * resolves or escalates produces a case in the corresponding status. The case
 * is the unit tracked on the support board (GET /api/cases).
 */
@Entity
@Table(name = "support_case")
@Getter
@Setter
@NoArgsConstructor
public class SupportCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference", unique = true, nullable = false, length = 64)
    private String reference;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "hotel_id")
    private String hotelId;

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
}
