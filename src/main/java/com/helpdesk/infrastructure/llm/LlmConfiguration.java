package com.helpdesk.infrastructure.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link HelpdeskLlmProperties} (BYOK) and the OpenAI-compatible
 * {@link LlmPort} implementation. When no API key is configured the port simply
 * reports {@code isConfigured() == false} and the app runs off-mode.
 */
@Configuration
@EnableConfigurationProperties(HelpdeskLlmProperties.class)
public class LlmConfiguration {
}
