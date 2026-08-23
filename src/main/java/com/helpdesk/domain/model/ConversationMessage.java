package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * A single turn in a {@link Conversation}. Every message — including system
 * notes — is persisted for full auditability ("why did the AI say this?").
 */
@Entity
@Table(name = "conversation_message")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private MessageKind kind;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "sop_id")
    private String sopId;

    @Column(name = "step_key")
    private String stepKey;

    @Column(name = "intent")
    private String intent;

    @Column(name = "step_result")
    private String stepResult;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public ConversationMessage(Conversation conversation, int seq, MessageRole role,
                               MessageKind kind, String content) {
        this.conversation = conversation;
        this.seq = seq;
        this.role = role;
        this.kind = kind;
        this.content = content;
    }
}
