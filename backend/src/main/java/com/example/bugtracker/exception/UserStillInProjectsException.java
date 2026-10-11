package com.example.bugtracker.exception;

/**
 * The account still belongs to projects. Deleting it would silently strip every
 * one of them of a member. Rendered as 409.
 */
public class UserStillInProjectsException extends RuntimeException {

    private final int projectCount;

    public UserStillInProjectsException(int projectCount) {
        super("This user still belongs to " + projectCount
                + (projectCount == 1 ? " project" : " projects")
                + ". Remove them from those projects first.");
        this.projectCount = projectCount;
    }

    public int getProjectCount() {
        return projectCount;
    }
}