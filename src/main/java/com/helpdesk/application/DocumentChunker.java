package com.helpdesk.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits extracted document text into overlapping word windows so the lexical
 * retriever can match a query against a meaningful span rather than the whole
 * document. Deterministic (no randomness) so indexing and retrieval are stable.
 */
@Component
public class DocumentChunker {

    private final int chunkSize;
    private final int overlap;

    public DocumentChunker() {
        this(200, 50);
    }

    public DocumentChunker(int chunkSize, int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be smaller than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)).trim());
            if (end == words.length) {
                break;
            }
            int next = end - overlap;
            if (next <= start) {
                next = end; // guarantee forward progress
            }
            start = next;
        }
        return chunks;
    }
}
