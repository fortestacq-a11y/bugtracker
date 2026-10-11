package com.example.bugtracker.service;

import com.example.bugtracker.exception.KeyAlreadyExistsException;
import com.example.bugtracker.exception.LastOwnerException;
import com.example.bugtracker.exception.ProjectHasIssuesException;

import com.example.bugtracker.domain.Project;
import com.example.bugtracker.domain.ProjectMember;
import com.example.bugtracker.domain.ProjectRole;
import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.IssueRepository;
import com.example.bugtracker.repository.ProjectMemberRepository;
import com.example.bugtracker.repository.ProjectRepository;
import com.example.bugtracker.repository.UserRepository;
import com.example.bugtracker.exception.NotFoundException;
import com.example.bugtracker.web.dto.ProjectDtos.AddMemberRequest;
import com.example.bugtracker.web.dto.ProjectDtos.MemberResponse;
import com.example.bugtracker.web.dto.ProjectDtos.ProjectRequest;
import com.example.bugtracker.web.dto.ProjectDtos.ProjectResponse;
import com.example.bugtracker.web.dto.ProjectDtos.UpdateMemberRoleRequest;
import com.example.bugtracker.web.dto.ProjectDtos.UserOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project CRUD and membership, all gated through {@link AccessControlService}. */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final IssueRepository issueRepository;
    private final AccessControlService access;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectMemberRepository memberRepository,
                          UserRepository userRepository,
                          IssueRepository issueRepository,
                          AccessControlService access) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.issueRepository = issueRepository;
        this.access = access;
    }

    /** Only the projects this caller can see — scoped in SQL, never post-filtered. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> listVisible(User user) {
        Set<Long> visible = access.visibleProjectIds(user);
        if (visible.isEmpty()) {
            return List.of();
        }
        return projectRepository.findAllVisible(visible).stream()
                .map(p -> ProjectResponse.from(
                        p,
                        access.roleIn(user, p.getId()).orElse(null),
                        issueRepository.countByProjectId(p.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id, User user) {
        Project project = requireVisible(id, user);
        return ProjectResponse.from(
                project,
                access.roleIn(user, project.getId()).orElse(null),
                issueRepository.countByProjectId(project.getId()));
    }

    /**
     * Creating a project makes you its OWNER. The membership row is written here
     * rather than left to the caller, so nobody can create a project they then
     * cannot administer.
     */
    @Transactional
    public ProjectResponse create(ProjectRequest request, User user) {
        String key = request.key().trim().toUpperCase();
        if (projectRepository.existsByProjectKeyIgnoreCase(key)) {
            throw new KeyAlreadyExistsException(key);
        }
        Project project = projectRepository.save(
                new Project(key, request.name().trim(), trimToNull(request.description())));

        memberRepository.save(new ProjectMember(project, user, ProjectRole.OWNER));
        return ProjectResponse.from(project, ProjectRole.OWNER, 0);
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request, User user) {
        Project project = requireVisible(id, user);
        access.requireOwnsProject(project, user);

        if (request.key() != null && !request.key().isBlank()) {
            String key = request.key().trim().toUpperCase();
            projectRepository.findByProjectKeyIgnoreCase(key)
                    .filter(existing -> !existing.getId().equals(project.getId()))
                    .ifPresent(existing -> {
                        throw new KeyAlreadyExistsException(key);
                    });
            project.setProjectKey(key);
        }
        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        project.setDescription(trimToNull(request.description()));

        return ProjectResponse.from(
                projectRepository.save(project),
                access.roleIn(user, project.getId()).orElse(null),
                issueRepository.countByProjectId(project.getId()));
    }

    /**
     * Deleting a project that still has issues is refused with 409 rather than
     * cascading — silently destroying a backlog is the worse surprise.
     */
    @Transactional
    public void delete(Long id, User user) {
        Project project = requireVisible(id, user);
        access.requireOwnsProject(project, user);

        long issueCount = issueRepository.countByProjectId(project.getId());
        if (issueCount > 0) {
            throw new ProjectHasIssuesException(issueCount);
        }

        memberRepository.findByProjectIdOrderByAddedAt(project.getId())
                .forEach(memberRepository::delete);
        projectRepository.delete(project);
    }

    // ---- membership ---------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long projectId, User user) {
        Project project = requireVisible(projectId, user);
        return memberRepository.findByProjectIdOrderByAddedAt(project.getId()).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public MemberResponse addMember(Long projectId, AddMemberRequest request, User user) {
        Project project = requireVisible(projectId, user);
        access.requireCanManageMembers(project, user);

        User invitee = userRepository.findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        ProjectMember existing = memberRepository
                .findByProjectIdAndUserId(project.getId(), invitee.getId())
                .orElse(null);
        if (existing != null) {
            // Idempotent: re-adding promotes the role instead of failing, so the
            // client needs no separate "is already a member" round-trip.
            existing.setProjectRole(request.role());
            return MemberResponse.from(memberRepository.save(existing));
        }
        return MemberResponse.from(
                memberRepository.save(new ProjectMember(project, invitee, request.role())));
    }

    @Transactional
    public MemberResponse updateMemberRole(Long projectId, Long userId,
                                           UpdateMemberRoleRequest request, User user) {
        Project project = requireVisible(projectId, user);
        access.requireCanManageMembers(project, user);

        ProjectMember member = memberRepository
                .findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new NotFoundException("That user is not a member of this project"));

        if (member.getProjectRole() == ProjectRole.OWNER && request.role() != ProjectRole.OWNER) {
            throw new LastOwnerException();
        }
        member.setProjectRole(request.role());
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public void removeMember(Long projectId, Long userId, User user) {
        Project project = requireVisible(projectId, user);
        access.requireCanManageMembers(project, user);

        ProjectMember member = memberRepository
                .findByProjectIdAndUserId(project.getId(), userId)
                .orElseThrow(() -> new NotFoundException("That user is not a member of this project"));

        if (member.getProjectRole() == ProjectRole.OWNER) {
            throw new LastOwnerException();
        }
        memberRepository.delete(member);
    }

    /**
     * Candidates for the "add member" dropdown, minus existing members. Restricted
     * to callers who can manage this project's members — this is the one endpoint
     * that lists other users, so opening it to non-managers would leak the whole
     * user directory. Names and emails only; never the password hash.
     */
    @Transactional(readOnly = true)
    public List<UserOption> listCandidates(Long projectId, User user) {
        Project project = requireVisible(projectId, user);
        access.requireCanManageMembers(project, user);

        Set<Long> existing = memberRepository.findAllInProjects(Set.of(project.getId())).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        return userRepository.findAll().stream()
                .filter(u -> !existing.contains(u.getId()))
                .map(UserOption::from)
                .toList();
    }

    private Project requireVisible(Long id, User user) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        access.requireProjectVisible(project, user);
        return project;
    }

    private String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}