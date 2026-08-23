package com.helpdesk.web.exception;

/** Thrown when a message is sent to an already-resolved/escalated conversation. */
public class ConversationClosedException extends RuntimeException {
    public ConversationClosedException(Long id) {
        super("Conversation is closed (resolved/escalated): " + id);
    }
}
