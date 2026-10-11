package com.example.bugtracker.service;

import com.example.bugtracker.domain.Issue;
import com.example.bugtracker.domain.IssueStatus;
import com.example.bugtracker.domain.Priority;
import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.IssueRepository;
import com.example.bugtracker.repository.ProjectRepository;
import com.example.bugtracker.web.dto.StatsDtos.PriorityCount;
import com.example.bugtracker.web.dto.StatsDtos.StatsResponse;
import com.example.bugtracker.web.dto.StatsDtos.StatusCount;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard counters.
 *
 * <p>Computed by walking the same scoped issue list the board uses, so the
 * numbers can never disagree with what the caller is allowed to see — an
 * aggregate over {@code findAll()} would leak the size of everyone else's backlog.
 */
@Service
public class StatsService {

    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;
    private final AccessControlService access;

    public StatsService(IssueRepository issueRepository,
                        ProjectRepository projectRepository,
                        AccessControlService access) {
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public StatsResponse statsFor(User user) {
        Set<Long> visible = access.visibleProjectIds(user);
        if (visible.isEmpty()) {
            return emptyStats();
        }

        List<Issue> issues = issueRepository.findVisible(visible, null, null, null, null, null, null);

        Map<IssueStatus, Long> byStatus = new EnumMap<>(IssueStatus.class);
        for (IssueStatus s : IssueStatus.values()) {
            byStatus.put(s, 0L);
        }
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            byPriority.put(p, 0L);
        }

        long unassigned = 0;
        long myAssigned = 0;
        for (Issue issue : issues) {
            byStatus.merge(issue.getStatus(), 1L, Long::sum);
            byPriority.merge(issue.getPriority(), 1L, Long::sum);
            if (issue.getAssignee() == null) {
                unassigned++;
            } else if (issue.getAssignee().getId().equals(user.getId())) {
                myAssigned++;
            }
        }

        long finished = byStatus.get(IssueStatus.DONE) + byStatus.get(IssueStatus.CLOSED);
        long total = issues.size();

        List<StatusCount> statusCounts = new ArrayList<>();
        for (IssueStatus s : IssueStatus.values()) {
            statusCounts.add(new StatusCount(s.name(), byStatus.get(s)));
        }
        List<PriorityCount> priorityCounts = new ArrayList<>();
        for (Priority p : Priority.values()) {
            priorityCounts.add(new PriorityCount(p.name(), byPriority.get(p)));
        }

        return new StatsResponse(
                total,
                total - finished,
                byStatus.get(IssueStatus.IN_PROGRESS),
                byStatus.get(IssueStatus.IN_REVIEW),
                finished,
                byStatus.get(IssueStatus.BACKLOG),
                projectRepository.findAllVisible(visible).size(),
                unassigned,
                myAssigned,
                statusCounts,
                priorityCounts);
    }

    private StatsResponse emptyStats() {
        List<StatusCount> statusCounts = new ArrayList<>();
        for (IssueStatus s : IssueStatus.values()) {
            statusCounts.add(new StatusCount(s.name(), 0L));
        }
        List<PriorityCount> priorityCounts = new ArrayList<>();
        for (Priority p : Priority.values()) {
            priorityCounts.add(new PriorityCount(p.name(), 0L));
        }
        return new StatsResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, statusCounts, priorityCounts);
    }
}