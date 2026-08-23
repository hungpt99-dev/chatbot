package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

/**
 * A screenshot (or other binary) attached to a conversation message. Attachments
 * are tenant-scoped by {@code hotelId} and parented to a {@link Conversation} so
 * they never leak across hotels. The raw bytes are stored inline (small
 * screenshots); {@code seq} ties the attachment back to the user message it
 * accompanied.
 */
@Entity
@Table(name = "message_attachment")
@Getter
@Setter
@NoArgsConstructor
public class MessageAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "hotel_id")
    private String hotelId;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "data")
    private byte[] data;

    @Column(name = "seq")
    private Integer seq;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public MessageAttachment(Conversation conversation, String hotelId, String contentType,
                             String fileName, byte[] data, Integer seq) {
        this.conversation = conversation;
        this.hotelId = hotelId;
        this.contentType = contentType;
        this.fileName = fileName;
        this.data = data;
        this.seq = seq;
    }
}
