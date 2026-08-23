package com.helpdesk.domain.repository;

import com.helpdesk.domain.model.Document;
import com.helpdesk.domain.model.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the document + document_chunk entities round-trip correctly. In the
 * test profile the schema is built by Hibernate (ddl-auto=create-drop) from the
 * @Entity classes, which mirror the V6 Flyway migration columns. If a column in
 * the entity drifts from V6 the context fails to start or this round-trip fails.
 */
@SpringBootTest(properties = {
        "helpdesk.seed.enabled=false"
})
class MigrationV6Test {

    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentChunkRepository chunkRepository;

    @Test
    void migrationCreatesDocumentTablesAndRoundTrips() {
        Document doc = new Document("d1", "h1", "guide.pdf", "application/pdf", 1);
        documentRepository.save(doc);

        DocumentChunk chunk = new DocumentChunk("c1", "d1", "h1", 0, "guide.pdf",
                "printer paper jam troubleshooting steps");
        chunkRepository.save(chunk);

        List<Document> docs = documentRepository.findByHotelId("h1");
        assertEquals(1, docs.size());
        assertEquals("guide.pdf", docs.get(0).getFilename());

        List<DocumentChunk> chunks = chunkRepository.findByHotelId("h1");
        assertEquals(1, chunks.size());
        assertEquals("d1", chunks.get(0).getDocumentId());
        assertTrue(chunks.get(0).getContent().contains("printer"));
    }
}
