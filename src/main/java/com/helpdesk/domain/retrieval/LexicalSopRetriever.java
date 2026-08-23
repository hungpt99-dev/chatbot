package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.repository.SopRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexical (keyword) retrieval over the SOP corpus. This is the MVP retrieval layer:
 * it scores each SOP by token overlap between the query and the SOP's searchable
 * fields (title, category, problem description, symptoms, and step instructions).
 *
 * It is intentionally deterministic and provider-free so the system runs with no LLM
 * configured. The interface is designed so an embeddings/vector retriever can replace
 * this implementation later without touching callers (see ADR-0002).
 */
@Component
public class LexicalSopRetriever {

    private final SopRepository sopRepository;

    public LexicalSopRetriever(SopRepository sopRepository) {
        this.sopRepository = sopRepository;
    }

    public RetrievalResult retrieve(String hotelId, String problemText) {
        List<Sop> all = sopRepository.findByHotelId(hotelId);
        if (all.isEmpty()) {
            return new RetrievalResult(List.of());
        }
        List<String> queryTokens = tokenize(problemText);
        if (queryTokens.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        List<Scored> scored = new ArrayList<>();
        for (Sop sop : all) {
            int score = score(sop, queryTokens);
            if (score > 0) {
                scored.add(new Scored(sop, score));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        // Relative threshold: keep only candidates within 50% of the best score. This
        // drops near-irrelevant SOPs that share only common words (e.g. generic Vietnamese
        // terms) while preserving genuine ties. The best candidate is always retained.
        List<Sop> candidates = new ArrayList<>();
        if (!scored.isEmpty()) {
            int best = scored.get(0).score;
            int floor = Math.max(1, (int) (best * 0.5));
            for (Scored s : scored) {
                if (s.score >= floor) {
                    candidates.add(s.sop);
                } else {
                    break;
                }
            }
        }
        return new RetrievalResult(candidates);
    }

    /**
     * Weighted lexical overlap. Field weights reflect diagnostic value: a match in the
     * title/category or a listed symptom is far stronger than a token appearing in some
     * step instruction. We do NOT divide by corpus size — doing so unfairly penalizes
     * SOPs with many steps — so the most *relevant* SOP (not the shortest) ranks first.
     */
    private int score(Sop sop, List<String> queryTokens) {
        int score = 0;
        score += 5 * overlap(queryTokens, tokenize(sop.getTitle()));
        score += 5 * overlap(queryTokens, tokenize(sop.getCategory()));
        score += 3 * overlap(queryTokens, tokenize(sop.getProblemDescription()));
        if (sop.getSymptoms() != null) {
            for (String s : sop.getSymptoms()) {
                score += 4 * overlap(queryTokens, tokenize(s));
            }
        }
        if (sop.getPrerequisites() != null) {
            for (String s : sop.getPrerequisites()) {
                score += 2 * overlap(queryTokens, tokenize(s));
            }
        }
        for (var step : sop.getSteps()) {
            score += 1 * overlap(queryTokens, tokenize(step.getInstruction()));
        }
        return score;
    }

    private int overlap(List<String> queryTokens, List<String> haystack) {
        if (haystack.isEmpty()) {
            return 0;
        }
        java.util.Set<String> set = new java.util.HashSet<>(haystack);
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

    private record Scored(Sop sop, int score) {}
}
