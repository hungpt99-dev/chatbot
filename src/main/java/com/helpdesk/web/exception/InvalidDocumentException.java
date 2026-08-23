package com.helpdesk.web.exception;

/** Thrown when an upload is rejected by validation (empty file, missing hotel, oversized). */
public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String message) {
        super(message);
    }
}
