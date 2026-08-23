package com.helpdesk.web.exception;

/** Thrown when SOP retrieval finds no candidate for a problem statement. */
public class NoSopFoundException extends RuntimeException {
    public NoSopFoundException(String problem) {
        super("No SOP matches the problem: " + problem);
    }
}
