package com.helpdesk.web;

import com.helpdesk.application.DocumentIngestionService;
import com.helpdesk.domain.retrieval.DocumentRetrievalResult;
import com.helpdesk.web.dto.DocumentChunkView;
import com.helpdesk.web.dto.DocumentMetadata;
import com.helpdesk.web.exception.InvalidDocumentException;
import com.helpdesk.web.exception.UnsupportedDocumentTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin KB document ingestion API (BRD §5). Thin HTTP boundary only: parse params,
 * call {@link DocumentIngestionService}, map to DTOs, set status. All mutating and
 * reading paths carry {@code hotelId} so the document corpus stays tenant-scoped.
 */
@RestController
@RequestMapping("/api/admin/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentMetadata upload(@RequestParam String hotelId,
                                   @RequestParam("file") MultipartFile file) {
        return ingestionService.upload(hotelId, file);
    }

    @GetMapping
    public List<DocumentMetadata> list(@RequestParam String hotelId) {
        return ingestionService.list(hotelId);
    }

    @GetMapping("/search")
    public List<DocumentChunkView> search(@RequestParam String hotelId,
                                          @RequestParam("q") String query) {
        DocumentRetrievalResult result = ingestionService.retrieve(hotelId, query);
        return result.chunks().stream().map(DocumentChunkView::from).toList();
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam String hotelId, @PathVariable String documentId) {
        ingestionService.delete(hotelId, documentId);
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<String> unsupported(UnsupportedDocumentTypeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<String> invalid(InvalidDocumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
