package com.helpdesk.domain.port;

/**
 * Boundary for translating employee-facing assistant text into the employee's
 * language. Implementations live in {@code infrastructure.translation} (e.g.
 * {@code RestTranslationAdapter}); the application layer calls this without
 * knowing the provider.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #isConfigured()} returns false when no translation provider is
 *       wired, so the caller falls back to passthrough (no translation).</li>
 *   <li>{@link #translate(String, String)} returns the translated text, or the
 *       original text unchanged when unconfigured / on a transient failure / for
 *       an unsupported target language. It must never throw into the request path.</li>
 * </ul>
 *
 * <p>Translation only ever touches the assistant's outgoing message text. The SOP
 * step instructions themselves stay in their authored language unless a translation
 * provider is configured to localize them separately.
 */
public interface TranslationPort {

    boolean isConfigured();

    String translate(String text, String targetLang);
}
