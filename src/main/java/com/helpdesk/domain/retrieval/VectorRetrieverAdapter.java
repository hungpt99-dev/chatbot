package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopEmbedding;
import com.helpdesk.domain.repository.SopEmbeddingRepository;
import com.helpdesk.domain.repository.SopRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Real in-process vector (semantic) retrieval behind {@link VectorRetrieverPort}.
 *
 * <p>It embeds the problem text and every hotel-scoped SOP with {@link EmbeddingService},
 * then ranks SOPs by cosine similarity. Embeddings are persisted in the in-process
 * vector store (table {@code sop_embedding}, accessed via {@link SopEmbeddingRepository})
 * and recomputed lazily only when a SOP's content hash changes — so no write path
 * in {@code SopService} needs to change and the store self-heals as SOPs evolve.
 *
 * <p>Tenant isolation is enforced by always loading SOPs and embeddings filtered on
 * {@code hotel_id}; the cosine ranking never reaches another hotel's corpus.
 */
@Component
public class VectorRetrieverAdapter implements VectorRetrieverPort {

    private static final int TOP_K = 5;
    private static final double MIN_SIMILARITY = 0.0;

    private final SopRepository sopRepository;
    private final SopEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;

    public VectorRetrieverAdapter(
            SopRepository sopRepository,
            SopEmbeddingRepository embeddingRepository,
            EmbeddingService embeddingService) {
        this.sopRepository = sopRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public RetrievalResult retrieve(String hotelId, String problemText) {
        List<Sop> sops = sopRepository.findByHotelId(hotelId);
        if (sops.isEmpty()) {
            return new RetrievalResult(List.of());
        }
        float[] query = embeddingService.embed(problemText);
        if (EmbeddingService.isZero(query)) {
            return new RetrievalResult(List.of());
        }

        List<Scored> scored = new ArrayList<>();
        for (Sop sop : sops) {
            float[] vec = embeddingFor(sop);
            double sim = EmbeddingService.cosine(query, vec);
            if (sim > MIN_SIMILARITY) {
                scored.add(new Scored(sop, sim));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<Sop> candidates = new ArrayList<>();
        for (Scored s : scored) {
            if (candidates.size() >= TOP_K) {
                break;
            }
            candidates.add(s.sop);
        }
        return new RetrievalResult(candidates);
    }

    /** Returns the stored embedding for a SOP, computing and persisting it if absent/changed. */
    private float[] embeddingFor(Sop sop) {
        String text = buildText(sop);
        String hash = contentHash(text);
        Optional<SopEmbedding> existing =
                embeddingRepository.findByHotelIdAndSopId(sop.getHotelId(), sop.getId());
        if (existing.isPresent() && hash.equals(existing.get().getContentHash())) {
            return EmbeddingService.parse(existing.get().getEmbedding());
        }
        float[] vec = embeddingService.embed(text);
        SopEmbedding row = existing.orElseGet(SopEmbedding::new);
        row.setHotelId(sop.getHotelId());
        row.setSopId(sop.getId());
        row.setContentHash(hash);
        row.setEmbedding(EmbeddingService.serialize(vec));
        embeddingRepository.save(row);
        return vec;
    }

    /** Composes the searchable corpus text for a SOP (same fields the lexical retriever weighs). */
    private String buildText(Sop sop) {
        StringBuilder sb = new StringBuilder();
        append(sb, sop.getTitle());
        append(sb, sop.getCategory());
        append(sb, sop.getProblemDescription());
        if (sop.getSymptoms() != null) {
            for (String s : sop.getSymptoms()) {
                append(sb, s);
            }
        }
        if (sop.getPrerequisites() != null) {
            for (String p : sop.getPrerequisites()) {
                append(sb, p);
            }
        }
        for (var step : sop.getSteps()) {
            append(sb, step.getInstruction());
        }
        return sb.toString();
    }

    private void append(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        sb.append(part).append(' ');
    }

    private String contentHash(String text) {
        return Integer.toHexString(text.hashCode());
    }

    private record Scored(Sop sop, double score) {}
}
