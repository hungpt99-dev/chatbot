package com.helpdesk.web;

import com.helpdesk.application.ConversationService;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@Valid @RequestBody ConversationRequest req) {
        return conversationService.create(req);
    }

    @PostMapping("/{id}/messages")
    public ConversationResponse sendMessage(@PathVariable Long id,
                                             @Valid @RequestBody MessageRequest req) {
        return conversationService.sendMessage(id, req);
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@PathVariable Long id) {
        return conversationService.get(id);
    }

    @ExceptionHandler(NoSopFoundException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<String> noSop(NoSopFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<String> notFound(ConversationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ConversationClosedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<String> closed(ConversationClosedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}
