package com.example.bugtracker.web;

import com.example.bugtracker.security.CurrentUser;
import com.example.bugtracker.service.ProjectService;
import com.example.bugtracker.web.dto.ProjectDtos.AddMemberRequest;
import com.example.bugtracker.web.dto.ProjectDtos.MemberResponse;
import com.example.bugtracker.web.dto.ProjectDtos.ProjectRequest;
import com.example.bugtracker.web.dto.ProjectDtos.ProjectResponse;
import com.example.bugtracker.web.dto.ProjectDtos.UpdateMemberRoleRequest;
import com.example.bugtracker.web.dto.ProjectDtos.UserOption;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/projects — CRUD plus membership management.
 *
 * <p>Every method reads the caller from the token and hands them to the service.
 * The controller makes no authorization decision of its own (plan §10.4).
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUser currentUser;

    public ProjectController(ProjectService projectService, CurrentUser currentUser) {
        this.projectService = projectService;
        this.currentUser = currentUser;
    }

    /** Only the projects this caller can see. */
    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.listVisible(currentUser.require());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return projectService.get(id, currentUser.require());
    }

    /** The creator is made OWNER of the new project, server-side. */
    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        ProjectResponse created = projectService.create(request, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** OWNER only. */
    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request, currentUser.require());
    }

    /** OWNER only, and refused with 409 while the project still has issues. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }

    // ---- membership ---------------------------------------------------------

    /** Members of this project — also the source for the assignee dropdown. */
    @GetMapping("/{id}/members")
    public List<MemberResponse> listMembers(@PathVariable Long id) {
        return projectService.listMembers(id, currentUser.require());
    }

    /** Candidates who could be added. MAINTAINER and above only. */
    @GetMapping("/{id}/members/candidates")
    public List<UserOption> listCandidates(@PathVariable Long id) {
        return projectService.listCandidates(id, currentUser.require());
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<MemberResponse> addMember(@PathVariable Long id,
                                                    @Valid @RequestBody AddMemberRequest request) {
        MemberResponse member = projectService.addMember(id, request, currentUser.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{id}/members/{userId}")
    public MemberResponse updateMemberRole(@PathVariable Long id,
                                           @PathVariable Long userId,
                                           @Valid @RequestBody UpdateMemberRoleRequest request) {
        return projectService.updateMemberRole(id, userId, request, currentUser.require());
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId, currentUser.require());
        return ResponseEntity.noContent().build();
    }
}