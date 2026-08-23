package com.helpdesk.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single searchable chunk of an uploaded knowledge-base document. Chunks are
 * the unit of lexical retrieval over the document corpus, mirroring how SOP steps
 * are the unit of SOP retrieval. Every chunk carries {@code hotelId} so document
 * retrieval can never cross tenant boundaries (see AGENTS.md §8).
 */
@Entity
@Table(name = "document_chunk")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(name = "hotel_id", nullable = false, length = 64)
    private String hotelId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "source_filename", length = 512)
    private String sourceFilename;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public DocumentChunk(String id, String documentId, String hotelId, int chunkIndex,
                        String sourceFilename, String content) {
        this.id = id;
        this.documentId = documentId;
        this.hotelId = hotelId;
        this.chunkIndex = chunkIndex;
        this.sourceFilename = sourceFilename;
        this.content = content;
    }
}
