package com.helpdesk.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A single turn in a {@link Conversation}. Every message — including system
 * notes — is persisted for full auditability ("why did the AI say this?").
 */
@Entity
@Table(name = "conversation_message")
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

    protected ConversationMessage() {}

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

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }
    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }
    public MessageKind getKind() { return kind; }
    public void setKind(MessageKind kind) { this.kind = kind; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSopId() { return sopId; }
    public void setSopId(String sopId) { this.sopId = sopId; }
    public String getStepKey() { return stepKey; }
    public void setStepKey(String stepKey) { this.stepKey = stepKey; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getStepResult() { return stepResult; }
    public void setStepResult(String stepResult) { this.stepResult = stepResult; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
