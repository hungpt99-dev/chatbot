package com.helpdesk.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight operational endpoint. Exposes whether an LLM key is configured so
 * the UI/ops can show off-mode vs online without leaking the secret.
 */
@RestController
public class HealthController {

    @Value("${helpdesk.llm.api-key:}")
    private String apiKey;

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        boolean llmConfigured = apiKey != null && !apiKey.isBlank();
        return Map.of(
                "status", "UP",
                "llmConfigured", llmConfigured,
                "mode", llmConfigured ? "online" : "offline");
    }
}
