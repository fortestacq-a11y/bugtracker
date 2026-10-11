package com.example.bugtracker.web.dto;

import java.time.Instant;

/** Response bodies for the failure paths handled by {@code GlobalExceptionHandler}. */
public final class ErrorDtos {

    private ErrorDtos() {
    }

    /**
     * One error shape for every failure, so the frontend has a single branch to
     * write. {@code status} is the HTTP code, echoed into the body.
     */
    public record ErrorResponse(
            int status,
            String error,
            String message,
            String path,
            Instant timestamp) {

        public static ErrorResponse of(int status, String error, String message, String path) {
            return new ErrorResponse(status, error, message, path, Instant.now());
        }
    }

    /** For endpoints that return 200 with a short confirmation. */
    public record MessageResponse(String message) {
    }
}