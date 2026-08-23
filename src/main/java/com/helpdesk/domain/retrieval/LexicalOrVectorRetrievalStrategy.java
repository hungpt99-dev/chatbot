package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.DocumentChunk;
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
 * SopService and the underlying retrievers.
 *
 * <p>HYBRID merges lexical and vector candidates, de-duplicated and ordered
 * lexical-first. Both backends are now real: {@link VectorRetrieverAdapter} ranks
 * hotel-scoped SOPs by cosine similarity over in-process embeddings.
 */
@Component
public class LexicalOrVectorRetrievalStrategy {

    private final LexicalSopRetriever lexical;
    private final VectorRetrieverPort vector;
    private final LexicalDocumentRetriever documentRetriever;
    private final VectorDocumentRetriever vectorDocumentRetriever;
    private final RetrievalMode mode;

    public LexicalOrVectorRetrievalStrategy(
            LexicalSopRetriever lexical,
            VectorRetrieverPort vector,
            LexicalDocumentRetriever documentRetriever,
            VectorDocumentRetriever vectorDocumentRetriever,
            @Value("${helpdesk.retrieval.mode:LEXICAL}") String mode) {
        this.lexical = lexical;
        this.vector = vector;
        this.documentRetriever = documentRetriever;
        this.vectorDocumentRetriever = vectorDocumentRetriever;
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
        return switch (mode) {
            case VECTOR -> vectorDocumentRetriever.retrieve(hotelId, query);
            case HYBRID -> mergeDocuments(
                    documentRetriever.retrieve(hotelId, query),
                    vectorDocumentRetriever.retrieve(hotelId, query));
            case LEXICAL -> documentRetriever.retrieve(hotelId, query);
        };
    }

    private DocumentRetrievalResult mergeDocuments(
            DocumentRetrievalResult primary, DocumentRetrievalResult fallback) {
        Set<String> seen = new LinkedHashSet<>();
        List<DocumentChunk> merged = new ArrayList<>();
        for (DocumentRetrievalResult r : List.of(primary, fallback)) {
            if (r == null || r.isEmpty()) continue;
            for (DocumentChunk c : r.chunks()) {
                if (seen.add(c.getId())) merged.add(c);
            }
        }
        return new DocumentRetrievalResult(merged);
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
