package com.helpdesk.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BYOK LLM configuration. The API key is expected to come from the environment
 * (e.g. {@code HELPDESK_LLM_API_KEY}) — never committed to the repo. When the key
 * is blank the port reports {@code isConfigured() == false} and the app runs in
 * off-mode (deterministic {@link com.helpdesk.domain.engine.OfflineInterpreter}).
 */
@ConfigurationProperties(prefix = "helpdesk.llm")
public record HelpdeskLlmProperties(
        String baseUrl,        // e.g. https://api.openai.com/v1
        String apiKey,         // from env HELPDESK_LLM_API_KEY
        String model,          // e.g. gpt-4o-mini
        String systemPrompt    // guidance for the assistant persona + SOP discipline
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
