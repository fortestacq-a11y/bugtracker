package com.example.bugtracker.web.dto;

import com.example.bugtracker.domain.Comment;
import com.example.bugtracker.domain.Environment;
import com.example.bugtracker.domain.Issue;
import com.example.bugtracker.domain.IssueHistory;
import com.example.bugtracker.domain.IssueStatus;
import com.example.bugtracker.domain.IssueType;
import com.example.bugtracker.domain.Priority;
import com.example.bugtracker.domain.Project;
import com.example.bugtracker.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * JSON shapes for /api/issues/**.
 *
 * <p>{@code permissions} travels with every issue so the UI can grey out actions
 * without a second round-trip. It is advisory: each endpoint re-checks on the
 * server and returns 403 regardless of what the client believed.
 */
public final class IssueDtos {

    private IssueDtos() {
    }

    public record CreateIssueRequest(
            @NotNull Long projectId,
            @NotBlank @Size(max = 200) String title,
            String description,
            IssueType type,
            Priority priority,
            Environment environment,
            Long assigneeId) {
    }

    /**
     * Full update. Every field is optional: null means "leave it alone", so a
     * partial form can save without first fetching the whole issue.
     */
    public record UpdateIssueRequest(
            @Size(max = 200) String title,
            String description,
            IssueType type,
            Priority priority,
            Environment environment,
            IssueStatus status,
            Long assigneeId) {
    }

    /** Status-only change, for Kanban drag-and-drop. */
    public record UpdateStatusRequest(@NotNull IssueStatus status) {
    }

    public record UserRef(Long id, String name, String email, String avatarColor) {

        public static UserRef from(User user) {
            if (user == null) {
                return null;
            }
            return new UserRef(user.getId(), user.getName(), user.getEmail(), user.getAvatarColor());
        }
    }

    public record ProjectRef(Long id, String key, String name) {

        public static ProjectRef from(Project project) {
            return new ProjectRef(project.getId(), project.getProjectKey(), project.getName());
        }
    }

    /** What the current caller may do with this issue. Advisory — see class note. */
    public record Permissions(
            boolean canEdit,
            boolean canTriage,
            boolean canDelete,
            boolean canComment) {
    }

    public record IssueResponse(
            Long id,
            String key,
            String title,
            String description,
            IssueType type,
            Priority priority,
            IssueStatus status,
            Environment environment,
            ProjectRef project,
            UserRef reporter,
            UserRef assignee,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long commentCount,
            Permissions permissions) {

        public static IssueResponse from(Issue issue, long commentCount, Permissions permissions) {
            return new IssueResponse(
                    issue.getId(),
                    issue.getIssueKey(),
                    issue.getTitle(),
                    issue.getDescription(),
                    issue.getType(),
                    issue.getPriority(),
                    issue.getStatus(),
                    issue.getEnvironment(),
                    ProjectRef.from(issue.getProject()),
                    UserRef.from(issue.getReporter()),
                    UserRef.from(issue.getAssignee()),
                    issue.getCreatedAt(),
                    issue.getUpdatedAt(),
                    issue.getResolvedAt(),
                    commentCount,
                    permissions);
        }
    }

    /** Single-issue view: the issue plus its comments and audit trail. */
    public record IssueDetailResponse(
            IssueResponse issue,
            List<CommentResponse> comments,
            List<HistoryResponse> history) {
    }

    public record CommentResponse(
            Long id,
            String body,
            UserRef author,
            Instant createdAt) {

        public static CommentResponse from(Comment comment) {
            return new CommentResponse(
                    comment.getId(),
                    comment.getBody(),
                    UserRef.from(comment.getAuthor()),
                    comment.getCreatedAt());
        }
    }

    public record CreateCommentRequest(@NotBlank @Size(max = 5000) String body) {
    }

    public record HistoryResponse(
            Long id,
            String field,
            String oldValue,
            String newValue,
            UserRef changedBy,
            Instant createdAt) {

        public static HistoryResponse from(IssueHistory history) {
            return new HistoryResponse(
                    history.getId(),
                    history.getField(),
                    history.getOldValue(),
                    history.getNewValue(),
                    UserRef.from(history.getChangedBy()),
                    history.getCreatedAt());
        }
    }
}