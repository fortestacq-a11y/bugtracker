package com.example.bugtracker.domain;

/**
 * Where an issue sits on the board. The Kanban columns map onto these:
 * BACKLOG, IN_PROGRESS, IN_REVIEW, DONE. CLOSED is DONE plus resolved.
 */
public enum IssueStatus {
    BACKLOG,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    CLOSED;

    /** DONE and CLOSED both count as finished for reporting. */
    public boolean isFinished() {
        return this == DONE || this == CLOSED;
    }
}