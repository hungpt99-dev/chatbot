package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SPIKE placeholder for embeddings/vector retrieval. It returns no candidates
 * because no embedding model or vector store is wired yet.
 *
 * <p>TODO(rag): replace with a real implementation that embeds {@code problemText},
 * queries a vector index (e.g. pgvector / hosted embeddings), and maps the hits
 * back to {@link Sop} entities wrapped in a
 * {@link RetrievalResult}. The caller contract ({@link VectorRetrieverPort}) and
 * the {@link RetrievalResult} type are already final, so this stub can be swapped
 * without touching SopService or the strategy.
 */
@Component
public class VectorRetrieverAdapter implements VectorRetrieverPort {

    @Override
    public RetrievalResult retrieve(String hotelId, String problemText) {
        return new RetrievalResult(List.of());
    }
}
