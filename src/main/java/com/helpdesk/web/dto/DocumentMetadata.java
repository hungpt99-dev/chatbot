package com.helpdesk.web.dto;

import com.helpdesk.domain.model.Document;

import java.time.Instant;

/**
 * Read model for uploaded-document metadata (no chunk text). Returned by the
 * admin upload/list endpoints.
 */
public record DocumentMetadata(
        String id,
        String hotelId,
        String filename,
        String contentType,
        int chunkCount,
        Instant uploadedAt
) {
    public static DocumentMetadata from(Document doc) {
        return new DocumentMetadata(
                doc.getId(), doc.getHotelId(), doc.getFilename(),
                doc.getContentType(), doc.getChunkCount(), doc.getUploadedAt());
    }
}
