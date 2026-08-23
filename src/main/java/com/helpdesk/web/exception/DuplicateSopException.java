package com.helpdesk.web.exception;

import lombok.Getter;

@Getter
public class DuplicateSopException extends RuntimeException {
    private final String id;

    public DuplicateSopException(String id) {
        super("SOP already exists: " + id);
        this.id = id;
    }
}
