package com.helpdesk.web;

/** Thrown when a support case reference does not exist. */
public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String reference) {
        super("Case not found: " + reference);
    }
}
