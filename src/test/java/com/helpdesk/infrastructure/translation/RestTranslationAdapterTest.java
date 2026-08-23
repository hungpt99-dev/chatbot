package com.helpdesk.infrastructure.translation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the {@link RestTranslationAdapter} degrade contract: when no provider
 * is configured it reports {@code isConfigured() == false} and {@link #translate}
 * returns the original text unchanged (passthrough), never throwing.
 */
class RestTranslationAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void unconfiguredReportsNotConfiguredAndPassesThrough() {
        HelpdeskTranslationProperties props =
                new HelpdeskTranslationProperties(null, "", null); // blank key => unconfigured
        RestTranslationAdapter adapter = new RestTranslationAdapter(props, mapper);

        assertFalse(adapter.isConfigured());
        String original = "Kiểm tra kẹt giấy";
        assertSame(original, adapter.translate(original, "en"));
        assertEquals(original, adapter.translate(original, "en"));
    }

    @Test
    void blankTargetLangPassesThroughWithoutCallingProvider() {
        HelpdeskTranslationProperties props =
                new HelpdeskTranslationProperties("https://translation.example.com/v1", "sk-test", "gpt-4o-mini");
        RestTranslationAdapter adapter = new RestTranslationAdapter(props, mapper);

        assertTrue(adapter.isConfigured());
        String original = "Máy in có bật không?";
        assertEquals(original, adapter.translate(original, "   "));
        assertEquals(original, adapter.translate(original, null));
    }
}
