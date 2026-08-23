package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.DocumentChunk;

import java.util.List;

/**
 * Result of retrieving candidate document chunks for a query. Ranked best-first.
 * Parallel to {@link RetrievalResult} but for the document (KB) corpus rather than
 * SOPs. The scoring technique is identical to {@link LexicalSopRetriever} so the
 * two corpora share one retrieval behavior (see BRD §5 / ADR-0002).
 */
public record DocumentRetrievalResult(List<DocumentChunk> chunks) {
    public boolean isEmpty() {
        return chunks == null || chunks.isEmpty();
    }
}
