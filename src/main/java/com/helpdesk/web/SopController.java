package com.helpdesk.web;

import com.helpdesk.application.SopService;
import com.helpdesk.domain.model.Sop;
import com.helpdesk.domain.retrieval.RetrievalResult;
import com.helpdesk.web.dto.SopRequest;
import com.helpdesk.web.dto.SopResponse;
import com.helpdesk.web.dto.SopSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sops")
public class SopController {

    private final SopService sopService;

    public SopController(SopService sopService) {
        this.sopService = sopService;
    }

    @GetMapping
    public List<SopSummary> list(@RequestParam(required = false) String category) {
        return sopService.list(category);
    }

    @GetMapping("/{id}")
    public SopResponse get(@PathVariable String id) {
        return sopService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SopResponse create(@Valid @RequestBody SopRequest req) {
        return sopService.create(req);
    }

    @PutMapping("/{id}")
    public SopResponse update(@PathVariable String id, @Valid @RequestBody SopRequest req) {
        return sopService.update(id, req);
    }

    /**
     * Retrieval endpoint (Phase 1A lexical; later RAG). Pure read, no state change.
     */
    @GetMapping("/search")
    public List<SopSummary> search(@RequestParam("q") String query) {
        return sopService.retrieve(query).candidates().stream()
                .map(s -> new SopSummary(s.getId(), s.getTitle(), s.getCategory(), s.getDescription()))
                .collect(Collectors.toList());
    }

    @ExceptionHandler(SopNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<String> notFound(SopNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateSopException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<String> duplicate(DuplicateSopException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
