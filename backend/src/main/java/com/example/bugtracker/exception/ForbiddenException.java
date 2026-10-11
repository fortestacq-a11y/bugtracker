package com.example.bugtracker.exception;

/**
 * Thrown when the caller holds a valid token but lacks the required project role.
 * Rendered as 403 — distinct from {@link UnauthenticatedException} on purpose, since
 * the frontend reacts differently: 401 logs out, 403 shows a toast and stays put.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public static ForbiddenException notMember() {
        return new ForbiddenException("You do not have access to this project");
    }

    public static ForbiddenException insufficientRole(String action) {
        return new ForbiddenException("Your role in this project does not allow you to " + action);
    }
}