package com.helpdesk.web;

public class SopNotFoundException extends RuntimeException {
    private final String id;

    public SopNotFoundException(String id) {
        super("SOP not found: " + id);
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
