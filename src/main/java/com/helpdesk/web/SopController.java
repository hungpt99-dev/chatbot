package com.helpdesk.web;

import com.helpdesk.application.SopService;
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
import com.helpdesk.web.exception.SopNotFoundException;
import com.helpdesk.web.exception.DuplicateSopException;

@RestController
@RequestMapping("/api/sops")
public class SopController {

    private final SopService sopService;

    public SopController(SopService sopService) {
        this.sopService = sopService;
    }

    @GetMapping
    public List<SopSummary> list(@RequestParam String hotelId,
                                 @RequestParam(required = false) String category) {
        return sopService.list(hotelId, category);
    }

    @GetMapping("/{code}")
    public SopResponse get(@RequestParam String hotelId, @PathVariable String code) {
        return sopService.get(hotelId, code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SopResponse create(@RequestParam String hotelId,
                              @Valid @RequestBody SopRequest req) {
        return sopService.create(hotelId, req);
    }

    @PutMapping("/{code}")
    public SopResponse update(@RequestParam String hotelId, @PathVariable String code,
                              @Valid @RequestBody SopRequest req) {
        return sopService.update(hotelId, code, req);
    }

    @GetMapping("/search")
    public List<SopSummary> search(@RequestParam String hotelId,
                                   @RequestParam("q") String query) {
        return sopService.retrieve(hotelId, query).candidates().stream()
                .map(s -> new SopSummary(s.getCode(), s.getTitle(), s.getCategory(), s.getDescription()))
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
