package com.helpdesk.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the admin document ingestion API (BRD §5). Verifies the
 * multipart upload endpoint indexes a document and that the search endpoint
 * returns the matching chunk, plus input validation (unsupported type, empty).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DocumentApiTest {

    private static final String HOTEL = "api-hotel";

    @Autowired MockMvc mvc;

    @Test
    void uploadAndSearchChunkViaApi() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "kb.txt", "text/plain",
                "The lobby printer keeps jamming paper near the fuser. Clean the rollers.".getBytes());

        mvc.perform(multipart("/api/admin/documents").file(file).param("hotelId", HOTEL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(HOTEL))
                .andExpect(jsonPath("$.filename").value("kb.txt"))
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mvc.perform(get("/api/admin/documents/search").param("hotelId", HOTEL).param("q", "printer paper jam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").exists());

        mvc.perform(get("/api/admin/documents").param("hotelId", HOTEL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("kb.txt"));
    }

    @Test
    void unsupportedTypeReturnsBadRequest() throws Exception {
        MockMultipartFile bad = new MockMultipartFile("file", "x.zip", "application/zip", "nope".getBytes());
        mvc.perform(multipart("/api/admin/documents").file(bad).param("hotelId", HOTEL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        mvc.perform(multipart("/api/admin/documents").file(empty).param("hotelId", HOTEL))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingHotelReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "kb.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/admin/documents").file(file))
                .andExpect(status().isBadRequest());
    }
}
