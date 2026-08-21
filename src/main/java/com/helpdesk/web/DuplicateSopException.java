package com.helpdesk.web;

public class DuplicateSopException extends RuntimeException {
    private final String id;

    public DuplicateSopException(String id) {
        super("SOP already exists: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
