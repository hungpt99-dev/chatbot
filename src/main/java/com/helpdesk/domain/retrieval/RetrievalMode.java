package com.helpdesk.domain.retrieval;

/**
 * Which retrieval backend(s) to use. Driven by {@code helpdesk.retrieval.mode}
 * (default {@link #LEXICAL}). The hybrid mode combines lexical + vector and
 * de-duplicates; with the current stub vector backend it degrades to lexical.
 */
public enum RetrievalMode {
    LEXICAL,
    VECTOR,
    HYBRID
}
