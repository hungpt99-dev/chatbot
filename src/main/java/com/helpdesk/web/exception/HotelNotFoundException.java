package com.helpdesk.web.exception;

/** Thrown when a hotel admin operation targets a hotel that does not exist. */
public class HotelNotFoundException extends RuntimeException {
    public HotelNotFoundException(String id) {
        super("Hotel not found: " + id);
    }
}
