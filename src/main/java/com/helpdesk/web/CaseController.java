package com.helpdesk.web;

import com.helpdesk.application.ConversationService;
import com.helpdesk.web.dto.CaseDetail;
import com.helpdesk.web.dto.CaseSummary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final ConversationService conversationService;

    public CaseController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<CaseSummary> list(@RequestParam(required = false) String hotelId,
                                  @RequestParam(required = false) String status) {
        return conversationService.listCases(hotelId, status);
    }

    @GetMapping("/{reference}")
    public CaseDetail get(@PathVariable String reference) {
        return conversationService.getCase(reference);
    }

    @ExceptionHandler(CaseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<String> notFound(CaseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
