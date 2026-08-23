package com.helpdesk.infrastructure.translation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Translation provider configuration (BYOK). Values come from
 * {@code helpdesk.translation.*} (e.g. {@code HELPDESK_TRANSLATION_API_KEY}); they
 * are never committed. When the API key is blank the port reports
 * {@code isConfigured() == false} and the app runs with passthrough (no translation).
 */
@ConfigurationProperties(prefix = "helpdesk.translation")
public record HelpdeskTranslationProperties(
        String baseUrl,    // e.g. https://translation.example.com/v1
        String apiKey,     // from env HELPDESK_TRANSLATION_API_KEY
        String model       // optional model / engine hint
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
