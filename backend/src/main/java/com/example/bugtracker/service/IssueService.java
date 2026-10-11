package com.example.bugtracker.service;

import com.example.bugtracker.exception.AssigneeNotMemberException;
import com.example.bugtracker.exception.AssigneeRequiredException;

import com.example.bugtracker.domain.Comment;
import com.example.bugtracker.domain.Environment;
import com.example.bugtracker.domain.Issue;
import com.example.bugtracker.domain.IssueHistory;
import com.example.bugtracker.domain.IssueStatus;
import com.example.bugtracker.domain.IssueType;
import com.example.bugtracker.domain.Priority;
import com.example.bugtracker.domain.Project;
import com.example.bugtracker.domain.ProjectRole;
import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.CommentRepository;
import com.example.bugtracker.repository.IssueHistoryRepository;
import com.example.bugtracker.repository.IssueRepository;
import com.example.bugtracker.repository.ProjectRepository;
import com.example.bugtracker.repository.UserRepository;
import com.example.bugtracker.exception.NotFoundException;
import com.example.bugtracker.web.dto.IssueDtos.CommentResponse;
import com.example.bugtracker.web.dto.IssueDtos.CreateCommentRequest;
import com.example.bugtracker.web.dto.IssueDtos.CreateIssueRequest;
import com.example.bugtracker.web.dto.IssueDtos.HistoryResponse;
import com.example.bugtracker.web.dto.IssueDtos.IssueDetailResponse;
import com.example.bugtracker.web.dto.IssueDtos.IssueResponse;
import com.example.bugtracker.web.dto.IssueDtos.Permissions;
import com.example.bugtracker.web.dto.IssueDtos.UpdateIssueRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issue CRUD. Every public method starts with an {@link AccessControlService} guard.
 *
 * <p>The list path never calls {@code findAll()} — it hands the caller's visible
 * project IDs to a scoped query, so the database only returns rows they may see.
 *
 * <p>Business rules enforced here (plan §5):
 * <ol>
 *   <li>issue key auto-generates from project key + counter → BUGTRK-1, BUGTRK-2</li>
 *   <li>DONE/CLOSED sets resolvedAt; moving back out of those states clears it</li>
 *   <li>status / priority / assignee changes each write an issue_history row</li>
 *   <li>IN_REVIEW requires an assignee</li>
 * </ol>
 */
@Service
public class IssueService {

    private static final String F_STATUS = "status";
    private static final String F_PRIORITY = "priority";
    private static final String F_ASSIGNEE = "assignee";
    private static final String F_TITLE = "title";
    private static final String F_TYPE = "type";
    private static final String F_ENVIRONMENT = "environment";

    private final IssueRepository issueRepository;
    private final IssueHistoryRepository historyRepository;
    private final CommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AccessControlService access;

    public IssueService(IssueRepository issueRepository,
                        IssueHistoryRepository historyRepository,
                        CommentRepository commentRepository,
                        ProjectRepository projectRepository,
                        UserRepository userRepository,
                        AccessControlService access) {
        this.issueRepository = issueRepository;
        this.historyRepository = historyRepository;
        this.commentRepository = commentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    // ---- reads --------------------------------------------------------------

    /** Scoped list with optional filters: projectId, status, priority, type, assigneeId, q. */
    @Transactional(readOnly = true)
    public List<IssueResponse> list(User user, Long projectId, IssueStatus status, Priority priority,
                                    IssueType type, Long assigneeId, String q) {
        Set<Long> visible = access.visibleProjectIds(user);
        if (visible.isEmpty()) {
            return List.of();
        }
        // Filtering by a project the caller isn't in yields nothing rather than 403 —
        // the list endpoint shouldn't be a way to probe for what exists.
        if (projectId != null && !visible.contains(projectId)) {
            return List.of();
        }

        String search = (q == null || q.isBlank()) ? null : q.trim();

        return issueRepository.findVisible(visible, projectId, status, priority, type, assigneeId, search)
                .stream()
                .map(i -> IssueResponse.from(
                        i, commentRepository.countByIssueId(i.getId()), permissionsFor(i, user)))
                .toList();
    }

    @Transactional(readOnly = true)
    public IssueResponse get(Long id, User user) {
        Issue issue = requireVisible(id, user);
        return IssueResponse.from(issue, commentRepository.countByIssueId(issue.getId()),
                permissionsFor(issue, user));
    }

    @Transactional(readOnly = true)
    public IssueDetailResponse getDetail(Long id, User user) {
        Issue issue = requireVisible(id, user);
        Set<Long> visible = Set.of(issue.getProject().getId());

        List<CommentResponse> comments = commentRepository.findVisible(visible, issue.getId()).stream()
                .map(CommentResponse::from)
                .toList();
        List<HistoryResponse> history = historyRepository.findVisible(visible, issue.getId()).stream()
                .map(HistoryResponse::from)
                .toList();

        return new IssueDetailResponse(
                IssueResponse.from(issue, comments.size(), permissionsFor(issue, user)),
                comments,
                history);
    }

    // ---- writes -------------------------------------------------------------

    @Transactional
    public IssueResponse create(CreateIssueRequest request, User user) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        access.requireCanCreateIssue(project, user);

        User assignee = resolveAssignee(request.assigneeId(), project);

        Issue issue = new Issue(
                nextIssueKey(project),
                request.title().trim(),
                trimToNull(request.description()),
                request.type() != null ? request.type() : IssueType.BUG,
                request.priority() != null ? request.priority() : Priority.P2,
                IssueStatus.BACKLOG,
                request.environment() != null ? request.environment() : Environment.DEV,
                project,
                user,
                assignee);

        Issue saved = issueRepository.save(issue);
        record(saved, "created", null,
                saved.getStatus() + " / " + saved.getPriority(), user);

        return IssueResponse.from(saved, 0, permissionsFor(saved, user));
    }

    @Transactional
    public IssueResponse update(Long id, UpdateIssueRequest request, User user) {
        Issue issue = requireVisible(id, user);

        // Triage fields (status / priority / assignee) need DEVELOPER. The rest only
        // needs edit rights, so a REPORTER can retitle their own bug but cannot
        // hand it to someone else or re-prioritise it.
        boolean triageAttempted = request.status() != null
                || request.priority() != null
                || request.assigneeId() != null;
        if (triageAttempted) {
            access.requireCanTriage(issue, user);
        } else {
            access.requireCanEdit(issue, user);
        }

        if (request.title() != null && !request.title().isBlank()
                && !request.title().trim().equals(issue.getTitle())) {
            record(issue, F_TITLE, issue.getTitle(), request.title().trim(), user);
            issue.setTitle(request.title().trim());
        }
        if (request.description() != null && !request.description().equals(issue.getDescription())) {
            record(issue, "description", truncate(issue.getDescription()), "<updated>", user);
            issue.setDescription(trimToNull(request.description()));
        }
        if (request.type() != null && request.type() != issue.getType()) {
            record(issue, F_TYPE, issue.getType().name(), request.type().name(), user);
            issue.setType(request.type());
        }
        if (request.environment() != null && request.environment() != issue.getEnvironment()) {
            record(issue, F_ENVIRONMENT, issue.getEnvironment().name(), request.environment().name(), user);
            issue.setEnvironment(request.environment());
        }
        if (request.priority() != null && request.priority() != issue.getPriority()) {
            record(issue, F_PRIORITY, issue.getPriority().name(), request.priority().name(), user);
            issue.setPriority(request.priority());
        }
        if (request.assigneeId() != null
                && !request.assigneeId().equals(
                        issue.getAssignee() == null ? null : issue.getAssignee().getId())) {
            User assignee = resolveAssignee(request.assigneeId(), issue.getProject());
            record(issue, F_ASSIGNEE,
                    issue.getAssignee() == null ? null : issue.getAssignee().getName(),
                    assignee == null ? null : assignee.getName(),
                    user);
            issue.setAssignee(assignee);
        }

        applyStatus(issue, request.status(), user);

        issue.setUpdatedAt(Instant.now());
        Issue saved = issueRepository.save(issue);
        return IssueResponse.from(saved, commentRepository.countByIssueId(saved.getId()),
                permissionsFor(saved, user));
    }

    /** Status-only change, for Kanban drag-and-drop. */
    @Transactional
    public IssueResponse updateStatus(Long id, IssueStatus status, User user) {
        Issue issue = requireVisible(id, user);
        access.requireCanTriage(issue, user);

        applyStatus(issue, status, user);

        issue.setUpdatedAt(Instant.now());
        Issue saved = issueRepository.save(issue);
        return IssueResponse.from(saved, commentRepository.countByIssueId(saved.getId()),
                permissionsFor(saved, user));
    }

    @Transactional
    public void delete(Long id, User user) {
        Issue issue = requireVisible(id, user);
        access.requireCanDeleteIssue(issue, user);

        // Children are removed explicitly. Leaving them would either orphan rows
        // against a deleted id or force a cascade the plan explicitly rules out.
        historyRepository.deleteByIssueId(issue.getId());
        commentRepository.deleteByIssueId(issue.getId());
        issueRepository.delete(issue);
    }

    // ---- comments -----------------------------------------------------------

    @Transactional
    public CommentResponse addComment(Long issueId, CreateCommentRequest request, User user) {
        Issue issue = requireVisible(issueId, user);
        access.requireCanComment(issue, user);

        return CommentResponse.from(
                commentRepository.save(new Comment(request.body().trim(), user, issue)));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long issueId, User user) {
        Issue issue = requireVisible(issueId, user);
        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issue.getId()).stream()
                .map(CommentResponse::from)
                .toList();
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Status transition with the resolvedAt rule and the IN_REVIEW guard. A real
     * change writes a history row; a no-op change writes nothing.
     */
    private void applyStatus(Issue issue, IssueStatus newStatus, User user) {
        if (newStatus == null || newStatus == issue.getStatus()) {
            return;
        }
        if (newStatus == IssueStatus.IN_REVIEW && issue.getAssignee() == null) {
            throw new AssigneeRequiredException();
        }

        record(issue, F_STATUS, issue.getStatus().name(), newStatus.name(), user);
        issue.setStatus(newStatus);

        // Rule 2: DONE and CLOSED count as resolved; moving back out clears it.
        issue.setResolvedAt(newStatus.isFinished() ? Instant.now() : null);
    }

    private void record(Issue issue, String field, String oldValue, String newValue, User user) {
        historyRepository.save(new IssueHistory(issue, field, oldValue, newValue, user));
    }

    /** Rule 1: PREFIX-N, where N is the highest existing number plus one. */
    private String nextIssueKey(Project project) {
        String prefix = project.getProjectKey();
        int next = issueRepository.findMaxIssueNumber(project.getId(), prefix.length()) + 1;
        return "%s-%d".formatted(prefix, next);
    }

    /**
     * An assignee must belong to the issue's project — otherwise you could hand
     * work to someone who is never able to open it.
     */
    private User resolveAssignee(Long assigneeId, Project project) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        boolean allowed = assignee.isAdmin()
                || access.roleIn(assignee, project.getId()).isPresent();
        if (!allowed) {
            throw new AssigneeNotMemberException();
        }
        return assignee;
    }

    /** Advisory permissions for the UI. Every endpoint still re-checks server-side. */
    private Permissions permissionsFor(Issue issue, User user) {
        var roleOpt = access.roleIn(user, issue.getProject().getId());
        if (roleOpt.isEmpty()) {
            return new Permissions(false, false, false, false);
        }
        ProjectRole role = roleOpt.get();
        boolean canEdit = role.canEditAnyIssue()
                || (role.atLeast(ProjectRole.REPORTER) && issue.isReportedBy(user));
        return new Permissions(
                canEdit,
                role.canEditAnyIssue(),
                role.canDeleteIssues(),
                role.atLeast(ProjectRole.REPORTER));
    }

    private Issue requireVisible(Long id, User user) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Issue not found"));
        access.requireVisible(issue, user);
        return issue;
    }

    private String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** History columns are VARCHAR(255); long descriptions get clipped, not rejected. */
    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 255 ? s : s.substring(0, 252) + "...";
    }
}