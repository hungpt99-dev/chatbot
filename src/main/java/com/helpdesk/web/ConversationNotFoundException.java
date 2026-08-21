package com.helpdesk.web;

/** Thrown when a conversation id does not exist. */
public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(Long id) {
        super("Conversation not found: " + id);
    }
}
