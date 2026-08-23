package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;

import java.util.List;

/**
 * Boundary for embeddings/vector (semantic) SOP retrieval. Parallel to
 * {@link LexicalSopRetriever}: given a hotel-scoped problem text, return ranked
 * candidate SOPs as a {@link RetrievalResult}.
 *
 * <p>The shipped implementation ({@code VectorRetrieverAdapter}) performs
 * in-process embedding + cosine ranking over an in-process vector store
 * (sop_embedding), with no external model. Swapping in a hosted embeddings/
 * pgvector backend later is an adapter-only change; callers (the retrieval
 * strategy + SopService) must not change.
 */
public interface VectorRetrieverPort {

    RetrievalResult retrieve(String hotelId, String problemText);
}
