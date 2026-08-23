package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Selects the active retrieval backend(s) based on {@code helpdesk.retrieval.mode}
 * (LEXICAL | VECTOR | HYBRID, default LEXICAL). This is the single seam between
 * SopService and the underlying retrievers, so adding a real vector backend later
 * is a config + adapter change only.
 *
 * <p>HYBRID merges lexical and vector candidates, de-duplicated and ordered
 * lexical-first. Because the shipped {@link VectorRetrieverAdapter} is a stub that
 * returns nothing, HYBRID currently degrades gracefully to lexical.
 */
@Component
public class LexicalOrVectorRetrievalStrategy {

    private final LexicalSopRetriever lexical;
    private final VectorRetrieverPort vector;
    private final LexicalDocumentRetriever documentRetriever;
    private final RetrievalMode mode;

    public LexicalOrVectorRetrievalStrategy(
            LexicalSopRetriever lexical,
            VectorRetrieverPort vector,
            LexicalDocumentRetriever documentRetriever,
            @Value("${helpdesk.retrieval.mode:LEXICAL}") String mode) {
        this.lexical = lexical;
        this.vector = vector;
        this.documentRetriever = documentRetriever;
        this.mode = parse(mode);
    }

    private static RetrievalMode parse(String raw) {
        try {
            return RetrievalMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return RetrievalMode.LEXICAL;
        }
    }

    public RetrievalResult retrieve(String hotelId, String problemText) {
        return switch (mode) {
            case VECTOR -> vector.retrieve(hotelId, problemText);
            case HYBRID -> merge(lexical.retrieve(hotelId, problemText),
                                  vector.retrieve(hotelId, problemText));
            case LEXICAL -> lexical.retrieve(hotelId, problemText);
        };
    }

    /**
     * Document (KB) corpus retrieval, scoped to the same hotel as the SOP corpus.
     * This is the single seam SopService uses, so uploaded documents are searched
     * through the same retrieval backend as SOPs.
     */
    public DocumentRetrievalResult retrieveDocuments(String hotelId, String query) {
        return documentRetriever.retrieve(hotelId, query);
    }

    private RetrievalResult merge(RetrievalResult primary, RetrievalResult fallback) {
        Set<String> seen = new LinkedHashSet<>();
        List<Sop> merged = new ArrayList<>();
        for (RetrievalResult r : List.of(primary, fallback)) {
            if (r == null || r.isEmpty()) continue;
            for (Sop s : r.candidates()) {
                if (seen.add(s.getId())) merged.add(s);
            }
        }
        return new RetrievalResult(merged);
    }
}
