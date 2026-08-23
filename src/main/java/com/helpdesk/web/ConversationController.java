package com.helpdesk.web;

import com.helpdesk.application.ConversationService;
import com.helpdesk.web.dto.ConversationRequest;
import com.helpdesk.web.dto.ConversationResponse;
import com.helpdesk.web.dto.MessageRequest;
import com.helpdesk.web.exception.ConversationClosedException;
import com.helpdesk.web.exception.ConversationNotFoundException;
import com.helpdesk.web.exception.NoSopFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    @PostMapping(value = "/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ConversationResponse sendMessage(@PathVariable Long id,
                                             @Valid @RequestBody MessageRequest req) {
        return conversationService.sendMessage(id, req);
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationResponse sendMessageWithAttachment(@PathVariable Long id,
                                                          @RequestParam(value = "message", required = false) String message,
                                                          @RequestParam(value = "branchKey", required = false) String branchKey,
                                                          @RequestPart(value = "image", required = false) MultipartFile image) {
        byte[] bytes = null;
        String contentType = null;
        if (image != null && !image.isEmpty()) {
            try {
                bytes = image.getBytes();
                contentType = image.getContentType();
            } catch (IOException e) {
                throw new RuntimeException("could not read uploaded image: " + e.getMessage(), e);
            }
        }
        return conversationService.sendMessage(id, message, branchKey, null, bytes, contentType);
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
