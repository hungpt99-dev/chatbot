package com.helpdesk.infrastructure.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.domain.port.VisionPort;
import com.helpdesk.infrastructure.llm.HelpdeskLlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible vision adapter (works with OpenAI, Azure OpenAI, and most
 * OpenAI-compatible gateways by changing {@code baseUrl}). It sends the screenshot
 * as a base64 {@code image_url} content part alongside the employee's question and
 * returns the model's textual description.
 *
 * <p>BYOK: reuses {@link HelpdeskLlmProperties} (the same API key / base URL / model
 * used for the chat LLM). When the key is blank the port reports
 * {@code isConfigured() == false} and {@link #analyze(byte[], String, String)}
 * returns {@code null} (off-mode) so the assistant simply proceeds without the
 * screenshot description. On any transport/parse failure it also returns {@code null}
 * — vision is a best-effort enrichment, never a hard dependency of the request.
 */
@Component
@Slf4j
public class RestVisionAdapter implements VisionPort {

    private final HelpdeskLlmProperties props;
    private final ObjectMapper mapper;
    private final RestClient client;

    @Autowired
    public RestVisionAdapter(HelpdeskLlmProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.client = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** Constructor that accepts a pre-built {@link RestClient} (used by tests to inject a mock server). */
    public RestVisionAdapter(HelpdeskLlmProperties props, ObjectMapper mapper, RestClient client) {
        this.props = props;
        this.mapper = mapper;
        this.client = (client != null) ? client : RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public String analyze(byte[] image, String contentType, String question) {
        if (!isConfigured()) return null;
        if (image == null || image.length == 0) return null;
        try {
            String body = buildRequestBody(image, contentType, question);
            String raw = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(raw);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Vision call failed; proceeding without screenshot description: {}", ex.getMessage());
            return null;
        }
    }

    private String buildRequestBody(byte[] image, String contentType, String question)
            throws JsonProcessingException {
        String questionText = (question == null || question.isBlank())
                ? "Describe this screenshot and identify any visible IT problem or error."
                : question;
        String mime = (contentType == null || contentType.isBlank()) ? "image/png" : contentType;
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);

        Map<String, Object> textPart = Map.of("type", "text", "text", questionText);
        Map<String, Object> imagePart = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUri));
        Map<String, Object> userMsg = Map.of(
                "role", "user",
                "content", List.of(textPart, imagePart));
        Map<String, Object> systemMsg = Map.of(
                "role", "system",
                "content", "You are a vision assistant for IT support. "
                        + "Describe the screenshot concisely and factually. Do not invent details.");
        Map<String, Object> req = Map.of(
                "model", props.model(),
                "messages", List.of(systemMsg, userMsg),
                "temperature", 0.2);
        return mapper.writeValueAsString(req);
    }

    String parse(String raw) throws JsonProcessingException {
        if (raw == null) return null;
        JsonNode root = mapper.readTree(raw);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return null;
        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) return null;
        return content.trim();
    }
}
