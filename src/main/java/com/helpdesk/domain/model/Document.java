package com.helpdesk.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Metadata for an uploaded knowledge-base document (PDF/DOCX/FAQ). The parsed,
 * chunked content lives in {@link DocumentChunk}; this row is the document-level
 * audit record (who uploaded what, when) and is hotel-scoped (multi-tenant).
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "filename", nullable = false, length = 512)
    private String filename;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public Document(String id, String hotelId, String filename, String contentType, int chunkCount) {
        this.id = id;
        this.hotelId = hotelId;
        this.filename = filename;
        this.contentType = contentType;
        this.chunkCount = chunkCount;
        this.uploadedAt = Instant.now();
    }
}
