package com.example.bugtracker.exception;

/** The account exists but has been disabled. Rendered as 403. */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("This account has been disabled");
    }
}