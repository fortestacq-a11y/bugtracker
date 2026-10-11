package com.example.bugtracker.exception;

/**
 * Wrong email or wrong password — deliberately the same exception for both, so
 * the login endpoint cannot be used to discover which emails have accounts.
 * Rendered as 401.
 */
public class BadCredentialsException extends RuntimeException {

    public BadCredentialsException() {
        super("Invalid email or password");
    }
}