package com.helpdesk.domain.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, in-process embedding provider. It does NOT call any external model
 * or API (consistent with the project's "no LLM required" philosophy and the
 * degrade-don't-fail rulebook). Instead it produces a deterministic bag-of-words
 * vector via the hashing trick: each token is mapped to a fixed-dimension index
 * (feature hashing) and weighted by a sublinear term frequency, then L2-normalized.
 *
 * <p>Cosine of two such vectors is a stable, provider-free semantic-ish signal
 * that lets {@link VectorRetrieverAdapter} rank SOPs without lexical token
 * matching. The dimension is fixed so a stored embedding and a query embedding
 * are always comparable. Swapping this for a real model later is a single-class
 * change behind {@link EmbeddingService} and does not touch the adapter's callers.
 */
@Component
public class EmbeddingService {

    /** Fixed embedding dimension. Stable across runs so stored vectors stay comparable. */
    public static final int DIM = 512;

    /**
     * Embeds free text into a normalized {@code float[DIM]} vector. An empty/blank
     * input yields the zero vector (no similarity to anything).
     */
    public float[] embed(String text) {
        float[] vec = new float[DIM];
        Map<String, Integer> tf = new HashMap<>();
        for (String token : tokenize(text)) {
            tf.merge(token, 1, Integer::sum);
        }
        if (tf.isEmpty()) {
            return vec;
        }
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            int idx = Math.floorMod(entry.getKey().hashCode(), DIM);
            // Sublinear TF damping: 1 + log(tf) keeps very frequent tokens from
            // dominating while still rewarding genuine repetition.
            vec[idx] += 1.0f + (float) Math.log(entry.getValue());
        }
        normalize(vec);
        return vec;
    }

    /** Cosine similarity in [0,1] for two L2-normalized vectors. */
    public static double cosine(float[] a, float[] b) {
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static boolean isZero(float[] v) {
        for (float x : v) {
            if (x != 0.0f) {
                return false;
            }
        }
        return true;
    }

    /** Serializes a vector to a compact CSV string for storage in TEXT columns. */
    public static String serialize(float[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.toString();
    }

    /** Inverse of {@link #serialize(String)}. Malformed input yields the zero vector. */
    public static float[] parse(String csv) {
        float[] v = new float[DIM];
        if (csv == null || csv.isBlank()) {
            return v;
        }
        String[] parts = csv.split(",");
        int n = Math.min(parts.length, DIM);
        for (int i = 0; i < n; i++) {
            try {
                v[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException ignored) {
                v[i] = 0.0f;
            }
        }
        return v;
    }

    private static void normalize(float[] v) {
        double norm = 0.0;
        for (float x : v) {
            norm += x * x;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0) {
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
    }

    /**
     * Tokenizes text for embedding. Mirrors {@link LexicalSopRetriever}'s tokenizer
     * (lowercase, strip punctuation, keep letters including Vietnamese ranges, drop
     * sub-2-char tokens) so query and SOP text live in the same feature space.
     */
    static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String raw : text.toLowerCase().split("\\s+")) {
            String t = raw.replaceAll("[^a-z0-9à-ỹ\\p{L}]", "").trim();
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }
}
