package com.example.bugtracker.exception;

/** Email already has an account. Rendered as 409 Conflict. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with that email already exists");
    }
}