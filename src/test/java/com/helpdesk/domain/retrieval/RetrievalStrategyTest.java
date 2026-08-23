package com.helpdesk.domain.retrieval;

import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.model.SopAssembler;
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
 * Verifies the retrieval strategy switches backends by mode. LEXICAL returns
 * candidates via keyword overlap; VECTOR returns candidates via in-process
 * embeddings (cosine); HYBRID merges both backends de-duplicated.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RetrievalStrategyTest {

    private static final String HOTEL = "test-hotel";

    @Autowired SopRepository sopRepository;
    @Autowired LexicalSopRetriever lexical;
    @Autowired VectorRetrieverAdapter vectorStub;
    @Autowired LexicalDocumentRetriever documentRetriever;
    @Autowired VectorDocumentRetriever vectorDocumentRetriever;

    private void seed() {
        SopRequest req = new SopRequest(
                "strat-printer", "Printer", "desc", "IT", "máy in không in được",
                List.of("máy in", "không in được"), List.of(), "e", "f", "esc",
                List.of(new StepRequest("1", 1, "x", SopStepType.QUESTION, "2", false, null, List.of()),
                        new StepRequest("2", 2, "done", SopStepType.ESCALATE, null, true, SopTerminalKind.ESCALATE, List.of())));
        sopRepository.save(SopAssembler.toEntity(req, HOTEL));
    }

    @Test
    void lexicalModeReturnsCandidates() {
        seed();
        LexicalOrVectorRetrievalStrategy s = new LexicalOrVectorRetrievalStrategy(lexical, vectorStub, documentRetriever, vectorDocumentRetriever, "LEXICAL");
        RetrievalResult r = s.retrieve(HOTEL, "Máy in không in được");
        assertFalse(r.isEmpty());
        assertEquals("strat-printer", r.candidates().get(0).getCode());
    }

    @Test
    void vectorModeReturnsCandidates() {
        seed();
        LexicalOrVectorRetrievalStrategy s = new LexicalOrVectorRetrievalStrategy(lexical, vectorStub, documentRetriever, vectorDocumentRetriever, "VECTOR");
        RetrievalResult r = s.retrieve(HOTEL, "Máy in không in được");
        assertFalse(r.isEmpty());
        assertEquals("strat-printer", r.candidates().get(0).getCode());
    }

    @Test
    void hybridModeMergesLexicalAndVector() {
        seed();
        LexicalOrVectorRetrievalStrategy s = new LexicalOrVectorRetrievalStrategy(lexical, vectorStub, documentRetriever, vectorDocumentRetriever, "HYBRID");
        RetrievalResult r = s.retrieve(HOTEL, "Máy in không in được");
        // both backends find the printer SOP; hybrid merges de-duplicated and non-empty
        assertFalse(r.isEmpty());
        assertEquals("strat-printer", r.candidates().get(0).getCode());
    }

    @Test
    void unknownModeDefaultsToLexical() {
        seed();
        LexicalOrVectorRetrievalStrategy s = new LexicalOrVectorRetrievalStrategy(lexical, vectorStub, documentRetriever, vectorDocumentRetriever, "bogus");
        RetrievalResult r = s.retrieve(HOTEL, "Máy in không in được");
        assertFalse(r.isEmpty());
    }
}
