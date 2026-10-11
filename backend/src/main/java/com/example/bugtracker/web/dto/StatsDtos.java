package com.example.bugtracker.web.dto;

import java.util.List;

/** Dashboard counters for {@code GET /api/stats}. */
public final class StatsDtos {

    private StatsDtos() {
    }

    /**
     * Dashboard counters, scoped to the projects the caller can see. Nothing here
     * is global — a non-member's numbers are computed from their own projects only.
     */
    public record StatsResponse(
            long totalIssues,
            long openIssues,
            long inProgress,
            long inReview,
            long done,
            long backlog,
            long totalProjects,
            long unassigned,
            long myAssigned,
            List<StatusCount> byStatus,
            List<PriorityCount> byPriority) {
    }

    public record StatusCount(String status, long count) {
    }

    public record PriorityCount(String priority, long count) {
    }
}