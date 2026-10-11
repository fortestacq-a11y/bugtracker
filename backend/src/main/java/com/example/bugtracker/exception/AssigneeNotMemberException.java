package com.example.bugtracker.exception;

/**
 * The chosen assignee is not a member of the issue's project, so they could never
 * open it. Rendered as 422.
 */
public class AssigneeNotMemberException extends RuntimeException {

    public AssigneeNotMemberException() {
        super("The assignee must be a member of this project");
    }
}