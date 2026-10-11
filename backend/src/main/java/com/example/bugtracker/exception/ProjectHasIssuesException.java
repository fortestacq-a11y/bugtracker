package com.example.bugtracker.exception;

/**
 * The project still has issues, so it cannot be deleted. Rendered as 409 —
 * deleting a backlog silently would be a far worse surprise than an error.
 */
public class ProjectHasIssuesException extends RuntimeException {

    private final long issueCount;

    public ProjectHasIssuesException(long issueCount) {
        super("This project still has " + issueCount
                + (issueCount == 1 ? " issue" : " issues") + ". Delete or move them first.");
        this.issueCount = issueCount;
    }

    public long getIssueCount() {
        return issueCount;
    }
}