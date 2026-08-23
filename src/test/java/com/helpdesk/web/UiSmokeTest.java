package com.helpdesk.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1D UI smoke test — verifies the static chat UI is actually served by the
 * running app (same-origin, no CORS). Hits the real HTTP layer on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UiSmokeTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void rootServesIndexHtml() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + "/", String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        String body = r.getBody();
        assertNotNull(body);
        assertTrue(body.contains("<!DOCTYPE html>") || body.toLowerCase().contains("<html"));
        assertTrue(body.contains("Hotel IT Assistant"));
    }

    @Test
    void uiAssetsAreServed() {
        assertEquals(HttpStatus.OK, rest.getForEntity("http://localhost:" + port + "/ui/app.js", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, rest.getForEntity("http://localhost:" + port + "/ui/styles.css", String.class).getStatusCode());
    }

    @Test
    void healthReportsMode() {
        ResponseEntity<String> r = rest.getForEntity("http://localhost:" + port + "/api/health", String.class);
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertTrue(r.getBody().contains("\"mode\""));
        assertTrue(r.getBody().contains("\"llmConfigured\""));
    }
}
