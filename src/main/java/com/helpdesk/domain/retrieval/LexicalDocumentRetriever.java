package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.DocumentChunk;
import com.helpdesk.domain.repository.DocumentChunkRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lexical (keyword) retrieval over the uploaded-document corpus. It mirrors
 * {@link LexicalSopRetriever}'s weighted token-overlap scoring so the document
 * corpus is indexed and searched with the same behavior as the SOP corpus. Hotel
 * scoping is enforced here (not just by prompt): a hotel's query only ever reaches
 * that hotel's chunks (AGENTS.md §8).
 */
@Component
public class LexicalDocumentRetriever {

    private final DocumentChunkRepository chunkRepository;

    public LexicalDocumentRetriever(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    public DocumentRetrievalResult retrieve(String hotelId, String query) {
        List<DocumentChunk> all = chunkRepository.findByHotelId(hotelId);
        if (all.isEmpty()) {
            return new DocumentRetrievalResult(List.of());
        }
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return new DocumentRetrievalResult(List.of());
        }

        List<Scored> scored = new ArrayList<>();
        for (DocumentChunk chunk : all) {
            int score = overlap(queryTokens, tokenize(chunk.getContent()));
            if (score > 0) {
                scored.add(new Scored(chunk, score));
            }
        }

        // Relative threshold: keep only chunks within 50% of the best score so that
        // near-irrelevant chunks sharing only a few common words are pruned, while the
        // best chunk is always retained. Mirrors LexicalSopRetriever.
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        List<DocumentChunk> candidates = new ArrayList<>();
        if (!scored.isEmpty()) {
            int best = scored.get(0).score;
            int floor = Math.max(1, (int) (best * 0.5));
            for (Scored s : scored) {
                if (s.score >= floor) {
                    candidates.add(s.chunk);
                } else {
                    break;
                }
            }
        }
        return new DocumentRetrievalResult(candidates);
    }

    private int overlap(List<String> queryTokens, List<String> haystack) {
        if (haystack.isEmpty()) {
            return 0;
        }
        Set<String> set = new HashSet<>(haystack);
        int hits = 0;
        for (String qt : queryTokens) {
            if (set.contains(qt)) {
                hits++;
            }
        }
        return hits;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase().split("\\s+")) {
            String t = raw.replaceAll("[^a-z0-9à-ỹ\\p{L}]", "").trim();
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }

    private record Scored(DocumentChunk chunk, int score) {}
}
