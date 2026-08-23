package com.helpdesk.web.exception;

/** Thrown when a conversation id does not exist. */
public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(Long id) {
        super("Conversation not found: " + id);
    }
}
