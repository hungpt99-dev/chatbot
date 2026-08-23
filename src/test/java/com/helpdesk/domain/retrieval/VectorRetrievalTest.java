package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopAssembler;
import com.helpdesk.domain.repository.SopEmbeddingRepository;
import com.helpdesk.domain.repository.SopRepository;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopStepType;
import com.helpdesk.web.dto.SopTerminalKind;
import com.helpdesk.web.dto.StepRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real in-process vector retrieval: embeddings are computed and stored in
 * sop_embedding, then ranked by cosine similarity, scoped by hotel_id.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VectorRetrievalTest {

    private static final String HOTEL = "vec-hotel";
    private static final String OTHER_HOTEL = "other-hotel";

    @Autowired SopRepository sopRepository;
    @Autowired SopEmbeddingRepository embeddingRepository;
    @Autowired VectorRetrieverAdapter vector;

    private void seed(String hotel, String id, String title, String problem, List<String> symptoms) {
        SopRequest req = new SopRequest(
                id, title, "desc", "IT", problem, symptoms, List.of(), "e", "f", "esc",
                List.of(new StepRequest("1", 1, "x", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "done", SopStepType.ESCALATE, null, true, SopTerminalKind.ESCALATE, List.of())));
        sopRepository.save(SopAssembler.toEntity(req, hotel));
    }

    @Test
    void retrievesPrinterSopByVietnameseCosine() {
        seed(HOTEL, "v-printer", "Printer cannot print", "máy in không in được", List.of("không in được", "máy in", "paper jam"));
        seed(HOTEL, "v-wifi", "WiFi cannot connect", "không kết nối wifi", List.of("wifi", "sai mật khẩu"));

        RetrievalResult r = vector.retrieve(HOTEL, "Máy in không in được");
        assertFalse(r.isEmpty());
        assertEquals("v-printer", r.candidates().get(0).getCode());
    }

    @Test
    void retrievesWifiSopByEnglishCosine() {
        seed(HOTEL, "v-printer", "Printer cannot print", "printer issue", List.of("printer", "paper jam"));
        seed(HOTEL, "v-wifi", "WiFi cannot connect", "wifi issue", List.of("wifi", "cannot connect"));

        RetrievalResult r = vector.retrieve(HOTEL, "I cannot connect to wifi");
        assertFalse(r.isEmpty());
        assertEquals("v-wifi", r.candidates().get(0).getCode());
    }

    @Test
    void noMatchForUnrelatedQuery() {
        seed(HOTEL, "v-printer", "Printer cannot print", "printer", List.of("printer"));
        RetrievalResult r = vector.retrieve(HOTEL, "cho tôi công thức nấu phở");
        assertTrue(r.isEmpty());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        seed(HOTEL, "v-printer", "Printer cannot print", "printer", List.of("printer"));
        RetrievalResult r = vector.retrieve(HOTEL, "");
        assertTrue(r.isEmpty());
    }

    @Test
    void embeddingsArePersistedAndHotelScoped() {
        seed(HOTEL, "v-printer", "Printer cannot print", "máy in không in được", List.of("máy in"));
        seed(OTHER_HOTEL, "o-printer", "Printer cannot print", "máy in không in được", List.of("máy in"));

        RetrievalResult r = vector.retrieve(HOTEL, "Máy in");

        // the returned candidate belongs to the requested hotel only
        assertFalse(r.isEmpty());
        assertEquals(HOTEL, r.candidates().get(0).getHotelId());

        // embeddings are lazily persisted per hotel and never cross tenants
        assertEquals(1, embeddingRepository.findByHotelId(HOTEL).size());
        assertEquals(0, embeddingRepository.findByHotelId(OTHER_HOTEL).size());

        // only after the other hotel is queried does its store get populated
        RetrievalResult other = vector.retrieve(OTHER_HOTEL, "Máy in");
        assertFalse(other.isEmpty());
        assertEquals(OTHER_HOTEL, other.candidates().get(0).getHotelId());
        assertEquals(1, embeddingRepository.findByHotelId(OTHER_HOTEL).size());
    }
}
