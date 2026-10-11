package com.example.bugtracker.exception;

/** The requested project key is already taken. Rendered as 409 Conflict. */
public class KeyAlreadyExistsException extends RuntimeException {

    public KeyAlreadyExistsException(String key) {
        super("Project key '" + key + "' is already in use");
    }
}