package com.helpdesk.application;

import com.helpdesk.domain.model.Document;
import com.helpdesk.domain.model.DocumentChunk;
import com.helpdesk.domain.repository.DocumentChunkRepository;
import com.helpdesk.domain.repository.DocumentEmbeddingRepository;
import com.helpdesk.domain.repository.DocumentRepository;
import com.helpdesk.domain.retrieval.DocumentRetrievalResult;
import com.helpdesk.domain.retrieval.VectorDocumentRetriever;
import com.helpdesk.web.dto.DocumentMetadata;
import com.helpdesk.web.exception.UnsupportedDocumentTypeException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves KB document ingestion end-to-end: parse (text/PDF/DOCX) -> chunk ->
 * index into the retrieval corpus -> hotel-scoped retrieval of a chunk. Uses the
 * in-memory test profile (Hibernate create-drop creates the document tables).
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DocumentIngestionTest {

    private static final String HOTEL_A = "doc-hotel-a";
    private static final String HOTEL_B = "doc-hotel-b";

    @Autowired DocumentIngestionService ingestionService;
    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentEmbeddingRepository embeddingRepository;
    @Autowired VectorDocumentRetriever vectorDocumentRetriever;

    @Test
    void textUploadChunksAndRetrievesChunk() {
        MockMultipartFile file = new MockMultipartFile("file", "kb.txt", "text/plain",
                "The reception printer keeps jamming paper near the fuser unit. Clean the rollers.".getBytes());

        DocumentMetadata meta = ingestionService.upload(HOTEL_A, file);

        assertNotNull(meta.id());
        assertEquals(HOTEL_A, meta.hotelId());
        assertEquals("kb.txt", meta.filename());
        assertTrue(meta.chunkCount() >= 1);
        assertEquals(1, documentRepository.findByHotelId(HOTEL_A).size());
        assertEquals(meta.chunkCount(), chunkRepository.findByHotelId(HOTEL_A).size());

        DocumentRetrievalResult res = ingestionService.retrieve(HOTEL_A, "printer paper jam");
        assertFalse(res.isEmpty(), "uploaded chunk should be retrievable");
        assertTrue(res.chunks().get(0).getContent().toLowerCase().contains("printer"));
    }

    @Test
    void vectorEmbeddingIndexesDocumentSemantically() {
        // Chunk wording differs from the query wording; lexical overlap is weak,
        // but the in-process embedding + cosine ranking must still surface it.
        MockMultipartFile file = new MockMultipartFile("file", "kb.txt", "text/plain",
                "Paper frequently becomes stuck inside the printing device near the heated roller.".getBytes());
        ingestionService.upload(HOTEL_A, file);

        // The embedding row must have been persisted on ingest (BRD §5 semantic index).
        assertFalse(embeddingRepository.findByHotelId(HOTEL_A).isEmpty(),
                "chunk embedding should be persisted on ingest");

        // Semantic query with distinct phrasing still retrieves the chunk via cosine.
        DocumentRetrievalResult res = vectorDocumentRetriever.retrieve(HOTEL_A, "printer keeps jamming paper");
        assertFalse(res.isEmpty(), "vector retrieval should surface the semantically similar chunk");
        assertTrue(res.chunks().get(0).getContent().toLowerCase().contains("paper"));
    }

    @Test
    void pdfUploadExtractsAndRetrieves() {
        byte[] pdf = buildPdf("Wireless printer offline troubleshooting steps for the lobby kiosk");
        MockMultipartFile file = new MockMultipartFile("file", "guide.pdf", "application/pdf", pdf);

        DocumentMetadata meta = ingestionService.upload(HOTEL_A, file);

        assertTrue(meta.chunkCount() >= 1);
        DocumentRetrievalResult res = ingestionService.retrieve(HOTEL_A, "printer offline troubleshooting");
        assertFalse(res.isEmpty(), "PDF content should be extracted and retrievable");
    }

    @Test
    void docxUploadExtractsAndRetrieves() {
        byte[] docx = buildDocx("VPN client password reset procedure for remote staff members");
        MockMultipartFile file = new MockMultipartFile("file", "vpn.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

        DocumentMetadata meta = ingestionService.upload(HOTEL_A, file);

        assertTrue(meta.chunkCount() >= 1);
        DocumentRetrievalResult res = ingestionService.retrieve(HOTEL_A, "vpn password reset");
        assertFalse(res.isEmpty(), "DOCX content should be extracted and retrievable");
    }

    @Test
    void retrievalIsTenantScoped() {
        MockMultipartFile file = new MockMultipartFile("file", "kb.txt", "text/plain",
                "The reception printer keeps jamming paper near the fuser unit.".getBytes());
        ingestionService.upload(HOTEL_A, file);

        // Hotel B has never uploaded anything; its query must not cross into A.
        DocumentRetrievalResult res = ingestionService.retrieve(HOTEL_B, "printer paper jam");
        assertTrue(res.isEmpty(), "document retrieval must not cross hotel boundaries");
        assertEquals(0, chunkRepository.findByHotelId(HOTEL_B).size());
    }

    @Test
    void unsupportedTypeRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "x.zip", "application/zip", "nope".getBytes());
        assertThrows(UnsupportedDocumentTypeException.class,
                () -> ingestionService.upload(HOTEL_A, file));
    }

    @Test
    void deletesDocumentAndChunks() {
        MockMultipartFile file = new MockMultipartFile("file", "kb.txt", "text/plain",
                "Password reset steps for the front desk terminal machine.".getBytes());
        DocumentMetadata meta = ingestionService.upload(HOTEL_A, file);
        assertEquals(1, documentRepository.findByHotelId(HOTEL_A).size());

        ingestionService.delete(HOTEL_A, meta.id());
        assertEquals(0, documentRepository.findByHotelId(HOTEL_A).size());
        assertEquals(0, chunkRepository.findByHotelId(HOTEL_A).size());
    }

    private byte[] buildPdf(String text) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(25, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] buildDocx(String text) {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
