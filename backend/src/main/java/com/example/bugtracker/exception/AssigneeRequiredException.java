package com.example.bugtracker.exception;

/** IN_REVIEW requires an assignee. Rendered as 422. */
public class AssigneeRequiredException extends RuntimeException {

    public AssigneeRequiredException() {
        super("An issue must have an assignee before it can move to IN_REVIEW");
    }
}