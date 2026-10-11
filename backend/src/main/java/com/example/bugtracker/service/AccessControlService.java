package com.example.bugtracker.service;

import com.example.bugtracker.domain.Issue;
import com.example.bugtracker.domain.Project;
import com.example.bugtracker.domain.ProjectRole;
import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.IssueRepository;
import com.example.bugtracker.repository.ProjectMemberRepository;
import com.example.bugtracker.repository.ProjectRepository;
import com.example.bugtracker.exception.ForbiddenException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every membership and role check in the app lives here.
 *
 * <p>Two rules, in order:
 * <ol>
 *   <li>Read: you can see an issue if you are an ADMIN, or a member of its project.</li>
 *   <li>Write: beyond that, your {@link ProjectRole} in that project decides.</li>
 * </ol>
 *
 * <p>The visible-project-ID set is computed here and handed to the repositories,
 * so the database itself does the scoping — no filtering in Java after fetchAll.
 */
@Service
public class AccessControlService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;

    public AccessControlService(ProjectMemberRepository projectMemberRepository,
                                ProjectRepository projectRepository,
                                IssueRepository issueRepository) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectRepository = projectRepository;
        this.issueRepository = issueRepository;
    }

    /**
     * Project IDs whose issues this caller may read.
     *
     * <p>For an ADMIN this is every project id — the membership table doesn't
     * apply. For everyone else it is exactly their memberships, and an empty set
     * correctly means "sees nothing".
     */
    @Transactional(readOnly = true)
    public Set<Long> visibleProjectIds(User user) {
        if (user.isAdmin()) {
            return projectRepository.findAll().stream()
                    .map(Project::getId)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return projectMemberRepository.findProjectIdsForUser(user.getId());
    }

    /**
     * The caller's role in a project, or empty when they are not a member.
     * ADMINs are not automatically members — they bypass checks instead, which is
     * modelled here as OWNER so every downstream guard passes.
     */
    @Transactional(readOnly = true)
    public Optional<ProjectRole> roleIn(User user, Long projectId) {
        if (user.isAdmin()) {
            return Optional.of(ProjectRole.OWNER);
        }
        return projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId())
                .map(m -> m.getProjectRole());
    }

    // ---- read guards --------------------------------------------------------

    /** The guard every public issue-facing read method calls first. */
    @Transactional(readOnly = true)
    public void requireVisible(Issue issue, User user) {
        if (user.isAdmin()) {
            return;
        }
        if (!isMember(user, issue.getProject().getId())) {
            throw ForbiddenException.notMember();
        }
    }

    /** Read access to a project itself. Same rule as for its issues. */
    @Transactional(readOnly = true)
    public void requireProjectVisible(Project project, User user) {
        if (user.isAdmin()) {
            return;
        }
        if (!isMember(user, project.getId())) {
            throw ForbiddenException.notMember();
        }
    }

    /**
     * Read access to an issue identified only by id — checked in SQL, before the
     * row is loaded, so a non-member gets 404 rather than a leaked existence check.
     */
    @Transactional(readOnly = true)
    public boolean canSeeIssue(Long issueId, User user) {
        if (user.isAdmin()) {
            return issueRepository.existsById(issueId);
        }
        Set<Long> visible = projectMemberRepository.findProjectIdsForUser(user.getId());
        if (visible.isEmpty()) {
            return false;
        }
        return issueRepository.findVisibleById(visible, issueId).isPresent();
    }

    // ---- write guards -------------------------------------------------------

    /** Edit an issue: DEVELOPER+ edits any; REPORTER edits only what they reported. */
    @Transactional(readOnly = true)
    public void requireCanEdit(Issue issue, User user) {
        requireVisible(issue, user);
        ProjectRole role = effectiveRole(user, issue.getProject().getId());
        if (role.canEditAnyIssue()) {
            return;
        }
        if (role.atLeast(ProjectRole.REPORTER) && issue.isReportedBy(user)) {
            return;
        }
        throw ForbiddenException.insufficientRole("edit this issue");
    }

    /** Change status / priority / assignee: DEVELOPER and above. */
    @Transactional(readOnly = true)
    public void requireCanTriage(Issue issue, User user) {
        requireVisible(issue, user);
        if (!effectiveRole(user, issue.getProject().getId()).canEditAnyIssue()) {
            throw ForbiddenException.insufficientRole("change status, priority or assignee");
        }
    }

    /** Delete an issue: MAINTAINER and above. */
    @Transactional(readOnly = true)
    public void requireCanDeleteIssue(Issue issue, User user) {
        requireVisible(issue, user);
        if (!effectiveRole(user, issue.getProject().getId()).canDeleteIssues()) {
            throw ForbiddenException.insufficientRole("delete issues");
        }
    }

    /** Create an issue in a project: REPORTER and above. */
    @Transactional(readOnly = true)
    public void requireCanCreateIssue(Project project, User user) {
        requireProjectVisible(project, user);
        if (!effectiveRole(user, project.getId()).atLeast(ProjectRole.REPORTER)) {
            throw ForbiddenException.insufficientRole("create issues");
        }
    }

    /** Comment on an issue: REPORTER and above. VIEWER is read-only. */
    @Transactional(readOnly = true)
    public void requireCanComment(Issue issue, User user) {
        requireVisible(issue, user);
        if (!effectiveRole(user, issue.getProject().getId()).atLeast(ProjectRole.REPORTER)) {
            throw ForbiddenException.insufficientRole("comment");
        }
    }

    /** Add or remove project members: MAINTAINER and above. */
    @Transactional(readOnly = true)
    public void requireCanManageMembers(Project project, User user) {
        requireProjectVisible(project, user);
        if (!effectiveRole(user, project.getId()).canDeleteIssues()) {
            throw ForbiddenException.insufficientRole("manage project members");
        }
    }

    /** Edit or delete the project itself: OWNER only. */
    @Transactional(readOnly = true)
    public void requireOwnsProject(Project project, User user) {
        requireProjectVisible(project, user);
        if (!effectiveRole(user, project.getId()).canManageProject()) {
            throw ForbiddenException.insufficientRole("edit or delete this project");
        }
    }

    private boolean isMember(User user, Long projectId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId());
    }

    private ProjectRole effectiveRole(User user, Long projectId) {
        return roleIn(user, projectId).orElseThrow(ForbiddenException::notMember);
    }
}