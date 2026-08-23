package com.helpdesk.infrastructure.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helpdesk.domain.model.SupportCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the degrade + forward contract of {@link RestTicketAdapter} without a
 * live Helpdesk: unset endpoint no-ops (returns null), and a configured endpoint
 * (backed by an in-memory stub transport) records the payload and returns the
 * external reference parsed from the provider response.
 */
class RestTicketAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SupportCase sampleCase() {
        SupportCase c = new SupportCase();
        c.setReference("CASE-000001");
        c.setConversationId(1L);
        c.setHotelId("hotel-1");
        c.setEmployee("jdoe");
        c.setProblem("Printer won't print");
        c.setSopId("printer");
        c.setSopTitle("Printer");
        c.setFailedStepKey("2");
        c.setEscalationReason("Still broken after all steps");
        return c;
    }

    @Test
    void unsetEndpointDegradesToNull() {
        HelpdeskTicketProperties props = new HelpdeskTicketProperties("");
        RestTicketAdapter adapter = new RestTicketAdapter(props, mapper);
        assertFalse(adapter.isConfigured());
        assertNull(adapter.raiseTicket(sampleCase()));
    }

    @Test
    void configuredEndpointRecordsPayloadAndReturnsExternalRef() {
        CapturingRequestFactory factory = new CapturingRequestFactory("{\"id\":\"EXT-98765\"}");
        HelpdeskTicketProperties props = new HelpdeskTicketProperties("https://helpdesk.example.com/tickets");
        RestClient client = RestClient.builder().requestFactory(factory).build();
        RestTicketAdapter adapter = new RestTicketAdapter(props, mapper, client);

        assertTrue(adapter.isConfigured());
        String ref = adapter.raiseTicket(sampleCase());

        assertEquals("EXT-98765", ref);
        // The adapter must POST the case payload (so a real endpoint receives it).
        String sent = factory.capturedBody.get();
        assertNotNull(sent);
        assertTrue(sent.contains("CASE-000001"));
        assertTrue(sent.contains("Still broken after all steps"));
    }

    @Test
    void plainTextResponseUsedVerbatimAsRef() {
        CapturingRequestFactory factory = new CapturingRequestFactory("TICKET-42");
        HelpdeskTicketProperties props = new HelpdeskTicketProperties("https://helpdesk.example.com/tickets");
        RestTicketAdapter adapter = new RestTicketAdapter(props, mapper,
                RestClient.builder().requestFactory(factory).build());
        assertEquals("TICKET-42", adapter.raiseTicket(sampleCase()));
    }

    @Test
    void transportErrorDegradesToNull() {
        ClientHttpRequestFactory failing = (uri, method) -> {
            throw new IllegalStateException("boom");
        };
        HelpdeskTicketProperties props = new HelpdeskTicketProperties("https://helpdesk.example.com/tickets");
        RestTicketAdapter adapter = new RestTicketAdapter(props, mapper,
                RestClient.builder().requestFactory(failing).build());
        assertNull(adapter.raiseTicket(sampleCase()));
    }

    @Test
    void unsetEndpointViaPropertiesRecord() {
        assertFalse(new HelpdeskTicketProperties(null).isConfigured());
        assertFalse(new HelpdeskTicketProperties("  ").isConfigured());
        assertTrue(new HelpdeskTicketProperties("https://x").isConfigured());
    }

    // ---- in-memory stub transport ----

    static class CapturingRequestFactory implements ClientHttpRequestFactory {
        final AtomicReference<String> capturedBody = new AtomicReference<>();
        private final String responseBody;

        CapturingRequestFactory(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
            return new StubRequest(uri, method, capturedBody, responseBody);
        }
    }

    static class StubRequest extends AbstractClientHttpRequest {
        private final URI uri;
        private final HttpMethod method;
        private final AtomicReference<String> captured;
        private final String responseBody;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        StubRequest(URI uri, HttpMethod method, AtomicReference<String> captured, String responseBody) {
            this.uri = uri;
            this.method = method;
            this.captured = captured;
            this.responseBody = responseBody;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        protected OutputStream getBodyInternal(HttpHeaders headers) {
            return buffer;
        }

        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers) {
            captured.set(buffer.toString(StandardCharsets.UTF_8));
            return new StubResponse(responseBody);
        }
    }

    static class StubResponse implements ClientHttpResponse {
        private final byte[] body;

        StubResponse(String body) {
            this.body = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpStatus getStatusCode() {
            return HttpStatus.OK;
        }

        @Override
        public int getRawStatusCode() {
            return 200;
        }

        @Override
        public String getStatusText() {
            return "OK";
        }

        @Override
        public void close() {
        }

        @Override
        public java.io.InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return HttpHeaders.EMPTY;
        }
    }
}
