package com.helpdesk.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.domain.engine.ConversationSnapshot;
import com.helpdesk.domain.engine.LlmStepDecision;
import com.helpdesk.domain.model.StepType;
import com.helpdesk.web.dto.SopResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the real parsing logic of {@link OpenAiCompatibleLlmPort} (strict-JSON,
 * markdown-fence stripping, malformed-content handling) without a live provider, plus
 * the {@code isConfigured()} / unconfigured-degrade contract. The wire transport is a
 * thin RestClient call; the risky, model-dependent part is the response parsing, which
 * is tested here directly. (A live LLM call remains out of scope — no key in CI.)
 */
class OpenAiCompatibleLlmPortTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HelpdeskLlmProperties props =
            new HelpdeskLlmProperties("https://api.example.com/v1", "sk-test", "gpt-4o-mini", "system");

    private OpenAiCompatibleLlmPort port() {
        return new OpenAiCompatibleLlmPort(props, mapper); // unconfigured client is fine; we call parse() directly
    }

    private ConversationSnapshot snapshot() {
        SopResponse.StepDto step = new SopResponse.StepDto("1", 1, "Powered on?",
                StepType.QUESTION, "2", false, null,
                List.of(new SopResponse.BranchDto("on", "bật", "2"),
                        new SopResponse.BranchDto("off", "tắt", "2")));
        return new ConversationSnapshot("7", "printer", "Printer", step, List.of("USER: máy in không in"));
    }

    @Test
    void parsesStrictJsonDecision() throws Exception {
        LlmStepDecision d = port().parse(
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"intent\\\":\\\"CONTINUE\\\",\\\"branchKey\\\":\\\"on\\\",\\\"stepResult\\\":\\\"máy in bật\\\",\\\"escalationReason\\\":null,\\\"response\\\":\\\"Tốt\\\"}\"}}]}");
        assertNotNull(d);
        assertEquals("CONTINUE", d.intent());
        assertEquals("on", d.branchKey());
        assertEquals("máy in bật", d.stepResult());
        assertEquals("Tốt", d.response());
    }

    @Test
    void stripsMarkdownFences() throws Exception {
        LlmStepDecision d = port().parse(
                "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"intent\\\":\\\"RESOLVE\\\",\\\"branchKey\\\":null,\\\"stepResult\\\":\\\"in được\\\",\\\"escalationReason\\\":null,\\\"response\\\":\\\"Xong\\\"}\\n```\"}}]}");
        assertNotNull(d);
        assertEquals("RESOLVE", d.intent());
        assertNull(d.branchKey());
    }

    @Test
    void malformedContentThrowsSoDecideDegrades() {
        // parse() throws on non-JSON content; decide() catches this and returns null
        // (offline fallback). Verify the contract the caller relies on.
        assertThrows(Exception.class, () -> port().parse("not json at all"));
    }

    @Test
    void emptyChoicesReturnsNull() throws Exception {
        assertNull(port().parse("{\"choices\":[]}"));
        assertNull(port().parse(null));
    }

    @Test
    void notConfiguredReturnsNullAndIsNotConfigured() {
        HelpdeskLlmProperties empty = new HelpdeskLlmProperties("https://x", "", "m", "s");
        OpenAiCompatibleLlmPort p = new OpenAiCompatibleLlmPort(empty, mapper);
        assertFalse(p.isConfigured());
        assertNull(p.decide(snapshot(), "hi"));
    }

    @Test
    void transportErrorDegradesToNull() {
        // A request factory that always fails makes decide() return null (offline fallback).
        org.springframework.http.client.ClientHttpRequestFactory failingFactory =
                new org.springframework.http.client.ClientHttpRequestFactory() {
                    @Override
                    public org.springframework.http.client.ClientHttpRequest createRequest(java.net.URI uri, org.springframework.http.HttpMethod method) {
                        throw new IllegalStateException("boom");
                    }
                };
        RestClient failing = RestClient.builder()
                .baseUrl("https://api.example.com/v1")
                .requestFactory(failingFactory)
                .build();
        OpenAiCompatibleLlmPort p = new OpenAiCompatibleLlmPort(props, mapper, failing);
        assertTrue(p.isConfigured());
        assertNull(p.decide(snapshot(), "hi")); // caught -> null, no exception escapes
    }
}
