package com.helpdesk.web.exception;

/** Thrown when an uploaded file is not a supported KB document type (PDF/DOCX/text/FAQ). */
public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
