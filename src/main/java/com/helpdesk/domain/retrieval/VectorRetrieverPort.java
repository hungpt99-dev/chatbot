package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;

import java.util.List;

/**
 * Boundary for embeddings/vector (semantic) SOP retrieval. Parallel to
 * {@link LexicalSopRetriever}: given a hotel-scoped problem text, return ranked
 * candidate SOPs as a {@link RetrievalResult}.
 *
 * <p>This is the SPI for the Phase 1F RAG spike. The shipped implementation
 * ({@code VectorRetrieverAdapter}) is a NO-OP stub because no embedding model or
 * vector store is wired yet. A production implementation would embed the query,
 * query pgvector (or a hosted index), and map rows back to {@link Sop} entities.
 * Callers (the retrieval strategy + SopService) must not change when this is
 * promoted from stub to real.
 */
public interface VectorRetrieverPort {

    RetrievalResult retrieve(String hotelId, String problemText);
}
