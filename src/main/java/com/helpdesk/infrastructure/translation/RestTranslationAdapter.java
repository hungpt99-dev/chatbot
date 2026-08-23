package com.helpdesk.infrastructure.translation;

import com.helpdesk.domain.port.TranslationPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * REST adapter for a translation provider behind {@link TranslationPort}. It is
 * intentionally provider-agnostic: it POSTs {@code {text, targetLang, model}} to
 * {@code {baseUrl}/translate} and expects {@code {translation}} back.
 *
 * <p>Degrade-don't-fail: when unconfigured ({@code isConfigured() == false}) or on
 * any transport/parse error, {@link #translate(String, String)} returns the original
 * text (passthrough) so the assistant stays on-script in the employee's fallback
 * language. The port never throws into the request path.
 */
@Component
@Slf4j
public class RestTranslationAdapter implements TranslationPort {

    private final HelpdeskTranslationProperties props;
    private final RestClient client;
    private final ObjectMapper mapper;

    @Autowired
    public RestTranslationAdapter(HelpdeskTranslationProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = buildClient(props, null);
    }

    /** Constructor accepting a pre-built {@link RestClient} (used by tests). */
    public RestTranslationAdapter(HelpdeskTranslationProperties props, ObjectMapper mapper, RestClient client) {
        this.props = props;
        this.mapper = mapper;
        this.client = buildClient(props, client);
    }

    private static RestClient buildClient(HelpdeskTranslationProperties props, RestClient supplied) {
        if (supplied != null) return supplied;
        RestClient.Builder b = RestClient.builder();
        if (props.baseUrl() != null && !props.baseUrl().isBlank()) {
            b = b.baseUrl(props.baseUrl());
        }
        return b
                .defaultHeader("Authorization", "Bearer " + (props.apiKey() == null ? "" : props.apiKey()))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public String translate(String text, String targetLang) {
        if (text == null || targetLang == null || targetLang.isBlank()) {
            return text;
        }
        if (!isConfigured()) {
            return text;
        }
        try {
            String body = mapper.writeValueAsString(new TranslationRequest(text, targetLang, props.model()));
            String raw = client.post()
                    .uri("/translate")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            TranslationResponse resp = mapper.readValue(raw, TranslationResponse.class);
            return (resp.translation() != null && !resp.translation().isBlank()) ? resp.translation() : text;
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Translation call failed; returning original text: {}", ex.getMessage());
            return text;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TranslationRequest(String text, String targetLang,
                                      @JsonProperty("model") String model) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TranslationResponse(String translation) {}
}
