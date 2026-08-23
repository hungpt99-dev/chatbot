package com.helpdesk.application;

import com.helpdesk.domain.model.Document;
import com.helpdesk.domain.model.DocumentChunk;
import com.helpdesk.domain.repository.DocumentChunkRepository;
import com.helpdesk.domain.repository.DocumentRepository;
import com.helpdesk.domain.retrieval.DocumentRetrievalResult;
import com.helpdesk.domain.retrieval.LexicalOrVectorRetrievalStrategy;
import com.helpdesk.infrastructure.document.DocumentContentExtractor;
import com.helpdesk.web.dto.DocumentMetadata;
import com.helpdesk.web.exception.InvalidDocumentException;
import com.helpdesk.web.exception.UnsupportedDocumentTypeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Document (KB) ingestion use case: validates an admin upload, extracts plain
 * text, chunks it, and indexes the chunks into the retrieval corpus scoped to the
 * hotel. Retrieval reuses the same {@link LexicalOrVectorRetrievalStrategy}
 * backend that SOP retrieval uses (AGENTS.md §7/§8), so uploaded knowledge is
 * searchable through the existing lexical retriever. The controller depends on
 * this; parsing/chunking/storage stay here, not in the web layer.
 */
@Service
public class DocumentIngestionService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentContentExtractor extractor;
    private final DocumentChunker chunker;
    private final LexicalOrVectorRetrievalStrategy retriever;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentChunkRepository chunkRepository,
                                    DocumentContentExtractor extractor,
                                    DocumentChunker chunker,
                                    LexicalOrVectorRetrievalStrategy retriever) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.extractor = extractor;
        this.chunker = chunker;
        this.retriever = retriever;
    }

    @Transactional
    public DocumentMetadata upload(String hotelId, MultipartFile file) {
        if (hotelId == null || hotelId.isBlank()) {
            throw new InvalidDocumentException("hotelId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("uploaded file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidDocumentException("uploaded file exceeds 10MB limit");
        }

        byte[] bytes = readBytes(file);
        String text = extractor.extract(file.getOriginalFilename(), file.getContentType(), bytes);
        if (text.isBlank()) {
            throw new InvalidDocumentException("document contained no extractable text");
        }

        List<String> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new InvalidDocumentException("document contained no extractable text");
        }

        String docId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String contentType = file.getContentType();

        Document doc = new Document(docId, hotelId, filename, contentType, chunks.size());
        documentRepository.save(doc);

        int index = 0;
        for (String chunkText : chunks) {
            DocumentChunk chunk = new DocumentChunk(
                    UUID.randomUUID().toString(), docId, hotelId, index, filename, chunkText);
            chunkRepository.save(chunk);
            index++;
        }

        return DocumentMetadata.from(doc);
    }

    public List<DocumentMetadata> list(String hotelId) {
        if (hotelId == null || hotelId.isBlank()) {
            throw new InvalidDocumentException("hotelId is required");
        }
        return documentRepository.findByHotelId(hotelId).stream()
                .map(DocumentMetadata::from)
                .toList();
    }

    public DocumentRetrievalResult retrieve(String hotelId, String query) {
        if (hotelId == null || hotelId.isBlank()) {
            throw new InvalidDocumentException("hotelId is required");
        }
        return retriever.retrieveDocuments(hotelId, query);
    }

    @Transactional
    public void delete(String hotelId, String documentId) {
        if (hotelId == null || hotelId.isBlank()) {
            throw new InvalidDocumentException("hotelId is required");
        }
        documentRepository.findByHotelIdAndId(hotelId, documentId)
                .stream().findFirst()
                .ifPresent(doc -> {
                    chunkRepository.deleteByHotelIdAndDocumentId(hotelId, documentId);
                    documentRepository.delete(doc);
                });
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new InvalidDocumentException("could not read uploaded file: " + e.getMessage());
        }
    }
}
