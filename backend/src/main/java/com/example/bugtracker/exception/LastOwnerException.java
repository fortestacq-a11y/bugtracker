package com.example.bugtracker.exception;

/**
 * A project must always keep at least one OWNER — otherwise nobody could ever
 * edit or delete it, and membership could never be repaired. Rendered as 409.
 */
public class LastOwnerException extends RuntimeException {

    public LastOwnerException() {
        super("A project must keep at least one owner");
    }
}