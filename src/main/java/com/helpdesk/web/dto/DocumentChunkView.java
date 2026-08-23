package com.helpdesk.web.dto;

import com.helpdesk.domain.model.DocumentChunk;

/**
 * Read model for a retrieved document chunk. Returned by the document search
 * endpoint so clients (and later the assistant) can surface KB passages.
 */
public record DocumentChunkView(
        String documentId,
        int chunkIndex,
        String sourceFilename,
        String content
) {
    public static DocumentChunkView from(DocumentChunk chunk) {
        return new DocumentChunkView(
                chunk.getDocumentId(), chunk.getChunkIndex(),
                chunk.getSourceFilename(), chunk.getContent());
    }
}
