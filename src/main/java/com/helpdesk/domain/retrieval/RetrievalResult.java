package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;

import java.util.List;

/**
 * Result of retrieving candidate SOPs for an employee's problem text.
 * Ranked best-first. This is the retrieval boundary; a lexical (FTS) implementation
 * ships in Phase 1A and an embeddings implementation can drop in later behind this type.
 */
public record RetrievalResult(List<Sop> candidates) {
    public boolean isEmpty() {
        return candidates == null || candidates.isEmpty();
    }
}
