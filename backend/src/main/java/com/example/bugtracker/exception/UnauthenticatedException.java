package com.example.bugtracker.exception;

/** Thrown when a request has no valid token. Rendered as 401. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Authentication required");
    }
}