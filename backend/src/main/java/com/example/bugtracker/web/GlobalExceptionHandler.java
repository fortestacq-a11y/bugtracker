package com.example.bugtracker.web;

import com.example.bugtracker.exception.ForbiddenException;
import com.example.bugtracker.exception.NotFoundException;
import com.example.bugtracker.exception.UnauthenticatedException;
import com.example.bugtracker.exception.AccountDisabledException;
import com.example.bugtracker.exception.AssigneeNotMemberException;
import com.example.bugtracker.exception.AssigneeRequiredException;
import com.example.bugtracker.exception.BadCredentialsException;
import com.example.bugtracker.exception.EmailAlreadyRegisteredException;
import com.example.bugtracker.exception.KeyAlreadyExistsException;
import com.example.bugtracker.exception.LastOwnerException;
import com.example.bugtracker.exception.ProjectHasIssuesException;
import com.example.bugtracker.exception.UserStillInProjectsException;
import com.example.bugtracker.web.dto.ErrorDtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One error shape for every failure, so the frontend has a single branch to write.
 *
 * <p>The 401 / 403 split is deliberate and load-bearing (plan §10.4):
 * <ul>
 *   <li><b>401</b> — no token, or an invalid/expired one. The client clears the
 *       token and redirects to login.</li>
 *   <li><b>403</b> — a valid token, but the role is too low. The client shows a
 *       toast and stays where it is.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- authentication -----------------------------------------------------

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(UnauthenticatedException e,
                                                               HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e,
                                                              HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    // ---- authorization ------------------------------------------------------

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e,
                                                          HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    /** Thrown by method-security checks; same meaning as ForbiddenException. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e,
                                                             HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to do that", request);
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(AccountDisabledException e,
                                                         HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    // ---- not found ----------------------------------------------------------

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e,
                                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    // ---- conflict -----------------------------------------------------------

    @ExceptionHandler({
            EmailAlreadyRegisteredException.class,
            KeyAlreadyExistsException.class,
            ProjectHasIssuesException.class,
            LastOwnerException.class,
            UserStillInProjectsException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException e,
                                                         HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    // ---- unprocessable ------------------------------------------------------

    @ExceptionHandler({
            AssigneeRequiredException.class,
            AssigneeNotMemberException.class})
    public ResponseEntity<ErrorResponse> handleUnprocessable(RuntimeException e,
                                                             HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), request);
    }

    // ---- bad requests -------------------------------------------------------

    /** Bean-validation failures: report each field problem in plain English. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                           HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** Malformed JSON — the parser's own message is noise for the client. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Request body could not be parsed", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    // ---- fallback -----------------------------------------------------------

    /**
     * Anything unanticipated becomes a 500 with a generic message. The real
     * exception is logged server-side, not returned — a stack trace in an API
     * response leaks internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message,
                                                 HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()));
    }
}