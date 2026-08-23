package com.helpdesk.web;

/** Thrown when creating a hotel whose id already exists. */
public class DuplicateHotelException extends RuntimeException {
    public DuplicateHotelException(String id) {
        super("Hotel already exists: " + id);
    }
}
