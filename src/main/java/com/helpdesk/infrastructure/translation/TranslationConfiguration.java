package com.helpdesk.infrastructure.translation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link HelpdeskTranslationProperties} and the {@link RestTranslationAdapter}
 * translation port. When no API key is configured the port reports
 * {@code isConfigured() == false} and the app falls back to passthrough (no translation).
 */
@Configuration
@EnableConfigurationProperties(HelpdeskTranslationProperties.class)
public class TranslationConfiguration {
}
