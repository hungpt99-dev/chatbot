package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted embedding for a {@link Sop} in the in-process vector store. One row
 * per (hotel_id, sop_id). The {@code embedding} column stores a CSV-serialized
 * fixed-dimension vector produced by {@link com.helpdesk.domain.retrieval.EmbeddingService};
 * {@code contentHash} lets the retriever detect when a SOP changed and recompute
 * the vector lazily instead of on every write path.
 *
 * <p>Hotel-scoped: {@code hotel_id} is always honored so retrieval never crosses
 * tenant boundaries (see AGENTS.md §8).
 */
@Entity
@Table(name = "sop_embedding")
@Getter
@Setter
@NoArgsConstructor
public class SopEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "sop_id", nullable = false, length = 64)
    private String sopId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "embedding", columnDefinition = "TEXT", nullable = false)
    private String embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
