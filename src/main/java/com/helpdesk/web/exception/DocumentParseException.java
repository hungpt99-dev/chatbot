package com.helpdesk.web.exception;

/** Thrown when a supported document cannot be parsed (corrupt PDF/DOCX, encoding error). */
public class DocumentParseException extends RuntimeException {
    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
