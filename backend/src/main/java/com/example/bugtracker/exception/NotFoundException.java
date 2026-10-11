package com.example.bugtracker.exception;

/** Thrown when an entity does not exist, or the caller may not know that it does. Rendered as 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}