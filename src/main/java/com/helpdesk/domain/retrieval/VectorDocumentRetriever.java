package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.DocumentChunk;
import com.helpdesk.domain.model.DocumentEmbedding;
import com.helpdesk.domain.repository.DocumentChunkRepository;
import com.helpdesk.domain.repository.DocumentEmbeddingRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Real in-process vector (semantic) retrieval over the uploaded-document corpus,
 * closing the BRD §5 gap: KB documents are indexed semantically and searched by
 * cosine similarity, not just keyword overlap.
 *
 * <p>It embeds the query and every hotel-scoped chunk with {@link EmbeddingService},
 * then ranks chunks by cosine similarity. Embeddings are persisted in the
 * in-process vector store (table {@code document_embedding}) and recomputed lazily
 * only when a chunk's content hash changes — so no separate write path is required
 * and the store self-heals as documents are re-uploaded (AGENTS.md §7/§8).</p>
 *
 * <p>Tenant isolation is enforced by always loading chunks and embeddings filtered
 * on {@code hotel_id}; the cosine ranking never reaches another hotel's corpus.</p>
 */
@Component
public class VectorDocumentRetriever {

    private static final int TOP_K = 5;
    private static final double MIN_SIMILARITY = 0.0;

    private final DocumentChunkRepository chunkRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;

    public VectorDocumentRetriever(
            DocumentChunkRepository chunkRepository,
            DocumentEmbeddingRepository embeddingRepository,
            EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
    }

    public DocumentRetrievalResult retrieve(String hotelId, String query) {
        List<DocumentChunk> chunks = chunkRepository.findByHotelId(hotelId);
        if (chunks.isEmpty()) {
            return new DocumentRetrievalResult(List.of());
        }
        float[] queryVec = embeddingService.embed(query);
        if (EmbeddingService.isZero(queryVec)) {
            return new DocumentRetrievalResult(List.of());
        }

        List<Scored> scored = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] vec = embeddingFor(hotelId, chunk);
            double sim = EmbeddingService.cosine(queryVec, vec);
            if (sim > MIN_SIMILARITY) {
                scored.add(new Scored(chunk, sim));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<DocumentChunk> candidates = new ArrayList<>();
        for (Scored s : scored) {
            if (candidates.size() >= TOP_K) {
                break;
            }
            candidates.add(s.chunk);
        }
        return new DocumentRetrievalResult(candidates);
    }

    /** Returns the stored embedding for a chunk, computing and persisting it if absent/changed. */
    private float[] embeddingFor(String hotelId, DocumentChunk chunk) {
        String text = chunk.getContent() == null ? "" : chunk.getContent();
        String hash = Integer.toHexString(text.hashCode());
        Optional<DocumentEmbedding> existing =
                embeddingRepository.findByHotelIdAndChunkId(hotelId, chunk.getId());
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            return EmbeddingService.parse(existing.get().getEmbedding());
        }
        float[] vec = embeddingService.embed(text);
        DocumentEmbedding row = existing.orElseGet(DocumentEmbedding::new);
        row.setHotelId(hotelId);
        row.setChunkId(chunk.getId());
        row.setDocumentId(chunk.getDocumentId());
        row.setContentHash(hash);
        row.setEmbedding(EmbeddingService.serialize(vec));
        embeddingRepository.save(row);
        return vec;
    }

    private record Scored(DocumentChunk chunk, double score) {}
}
