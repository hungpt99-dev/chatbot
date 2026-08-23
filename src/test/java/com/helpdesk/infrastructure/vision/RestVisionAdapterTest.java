package com.helpdesk.infrastructure.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.infrastructure.llm.HelpdeskLlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the {@link RestVisionAdapter} contract: response parsing, and the
 * degrade-to-null behavior when unconfigured or on transport failure. No live
 * provider is contacted (no key in CI).
 */
class RestVisionAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HelpdeskLlmProperties props =
            new HelpdeskLlmProperties("https://api.example.com/v1", "sk-test", "gpt-4o-mini", "system");

    private RestVisionAdapter adapter() {
        return new RestVisionAdapter(props, mapper);
    }

    @Test
    void parsesVisionDescription() throws Exception {
        String raw = "{\"choices\":[{\"message\":{\"content\":\"The power LED is blinking red.\"}}]}";
        assertEquals("The power LED is blinking red.", adapter().parse(raw));
    }

    @Test
    void emptyChoicesReturnsNull() throws Exception {
        assertNull(adapter().parse("{\"choices\":[]}"));
        assertNull(adapter().parse(null));
    }

    @Test
    void unconfiguredReturnsNullAndIsNotConfigured() {
        HelpdeskLlmProperties empty = new HelpdeskLlmProperties("https://x", "", "m", "s");
        RestVisionAdapter a = new RestVisionAdapter(empty, mapper);
        assertFalse(a.isConfigured());
        assertNull(a.analyze(new byte[]{1}, "image/png", "q"));
    }

    @Test
    void nullOrEmptyImageReturnsNull() {
        assertTrue(adapter().isConfigured());
        assertNull(adapter().analyze(null, "image/png", "q"));
        assertNull(adapter().analyze(new byte[0], "image/png", "q"));
    }

    @Test
    void transportErrorDegradesToNull() {
        ClientHttpRequestFactory failingFactory = new ClientHttpRequestFactory() {
            @Override
            public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
                throw new IllegalStateException("boom");
            }
        };
        RestClient failing = RestClient.builder()
                .baseUrl("https://api.example.com/v1")
                .requestFactory(failingFactory)
                .build();
        RestVisionAdapter a = new RestVisionAdapter(props, mapper, failing);
        assertTrue(a.isConfigured());
        assertNull(a.analyze(new byte[]{1, 2, 3}, "image/png", "describe"));
    }
}
