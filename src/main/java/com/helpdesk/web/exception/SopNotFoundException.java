package com.helpdesk.web.exception;

import lombok.Getter;

@Getter
public class SopNotFoundException extends RuntimeException {
    private final String id;

    public SopNotFoundException(String id) {
        super("SOP not found: " + id);
        this.id = id;
    }
}
