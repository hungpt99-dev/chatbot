package com.helpdesk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted embedding for a {@link DocumentChunk} in the in-process vector store.
 * One row per (hotel_id, chunk_id). Mirrors {@link SopEmbedding}: the {@code embedding}
 * column stores a CSV-serialized fixed-dimension vector from
 * {@link com.helpdesk.domain.retrieval.EmbeddingService}, and {@code contentHash}
 * lets the retriever lazily recompute only when a chunk's text changes.
 *
 * <p>This is what closes the BRD §5 gap: uploaded KB documents (PDF/DOCX/FAQ) are
 * indexed semantically, not just lexically, and searched by cosine similarity
 * through {@link com.helpdesk.domain.retrieval.VectorDocumentRetriever}.</p>
 *
 * <p>Hotel-scoped: {@code hotel_id} is always honored so retrieval never crosses
 * tenant boundaries (AGENTS.md §8).</p>
 */
@Entity
@Table(name = "document_embedding")
@Getter
@Setter
@NoArgsConstructor
public class DocumentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "chunk_id", nullable = false, length = 64)
    private String chunkId;

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

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
