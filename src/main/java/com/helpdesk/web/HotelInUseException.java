package com.helpdesk.web;

/** Thrown when a hotel cannot be deleted because it still owns SOPs/conversations. */
public class HotelInUseException extends RuntimeException {
    public HotelInUseException(String id) {
        super("Hotel cannot be deleted because it still has SOPs or conversations: " + id);
    }
}
