package com.example.bugtracker.web.dto;

import com.example.bugtracker.domain.Project;
import com.example.bugtracker.domain.ProjectMember;
import com.example.bugtracker.domain.ProjectRole;
import com.example.bugtracker.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * JSON shapes for /api/projects/**.
 *
 * <p>{@code myRole} is included on the project response so the frontend can hide
 * buttons the caller cannot use. That is UX only — the endpoint still enforces it.
 */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{0,9}$",
                    message = "key must be 1-10 letters/digits, starting with a letter")
            String key,
            @NotBlank @Size(max = 120) String name,
            String description) {
    }

    public record ProjectResponse(
            Long id,
            String key,
            String name,
            String description,
            Instant createdAt,
            ProjectRole myRole,
            long issueCount) {

        public static ProjectResponse from(Project project, ProjectRole myRole, long issueCount) {
            return new ProjectResponse(
                    project.getId(),
                    project.getProjectKey(),
                    project.getName(),
                    project.getDescription(),
                    project.getCreatedAt(),
                    myRole,
                    issueCount);
        }
    }

    public record MemberResponse(
            Long id,
            String name,
            String email,
            String avatarColor,
            ProjectRole role,
            Instant addedAt) {

        public static MemberResponse from(ProjectMember member) {
            User u = member.getUser();
            return new MemberResponse(
                    u.getId(),
                    u.getName(),
                    u.getEmail(),
                    u.getAvatarColor(),
                    member.getProjectRole(),
                    member.getAddedAt());
        }
    }

    public record AddMemberRequest(
            @NotNull Long userId,
            @NotNull ProjectRole role) {
    }

    public record UpdateMemberRoleRequest(@NotNull ProjectRole role) {
    }

    /**
     * Candidate users to add to a project. Exposes only id, name and email —
     * never the password hash — and is served only to callers who can already
     * manage this project's members.
     */
    public record UserOption(Long id, String name, String email, String avatarColor) {

        public static UserOption from(User user) {
            return new UserOption(user.getId(), user.getName(), user.getEmail(), user.getAvatarColor());
        }
    }
}