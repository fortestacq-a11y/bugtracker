package com.example.bugtracker.domain;

/**
 * Per-project ability. Ordered least → most capable, so comparisons like
 * {@code role.ordinal() >= DEVELOPER.ordinal()} work for "at least a developer".
 *
 * <pre>
 *   VIEWER      read only
 *   REPORTER    + create issues, comment, edit own issues only
 *   DEVELOPER   + edit any issue, change status / priority / assignee
 *   MAINTAINER  + delete issues, manage members
 *   OWNER       + edit / delete the project itself
 * </pre>
 */
public enum ProjectRole {
    VIEWER,
    REPORTER,
    DEVELOPER,
    MAINTAINER,
    OWNER;

    /** True when this role is at least as capable as {@code required}. */
    public boolean atLeast(ProjectRole required) {
        return ordinal() >= required.ordinal();
    }

    /** Convenience for the many "developer or better" checks. */
    public boolean canEditAnyIssue() {
        return atLeast(DEVELOPER);
    }

    /** True for MAINTAINER and OWNER. */
    public boolean canDeleteIssues() {
        return atLeast(MAINTAINER);
    }

    /** True for OWNER only — managing the project itself is the owner's job. */
    public boolean canManageProject() {
        return this == OWNER;
    }
}