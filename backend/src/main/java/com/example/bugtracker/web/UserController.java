package com.example.bugtracker.web;

import com.example.bugtracker.security.CurrentUser;
import com.example.bugtracker.service.UserService;
import com.example.bugtracker.web.dto.AuthDtos.UserResponse;
import com.example.bugtracker.web.dto.IssueDtos.ProjectRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/users — scoped to the account itself.
 *
 * <p>There is intentionally no {@code GET /api/users}. See {@link UserService} for
 * why: a global user list would expose every account's name and email to anyone
 * holding a valid token. The assignee dropdown uses
 * {@code GET /api/projects/{id}/members} instead.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    public record UpdateProfileRequest(
            @Size(max = 80) String name,
            @Email @Size(max = 160) String email) {

        @AssertTrue(message = "provide at least one field to update")
        public boolean isSomethingSet() {
            return (name != null && !name.isBlank()) || (email != null && !email.isBlank());
        }
    }

    @GetMapping("/me")
    public UserResponse me() {
        return userService.me(currentUser.require());
    }

    /** Self or admin; anyone else gets 404, so this cannot be used to probe ids. */
    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.getById(id, currentUser.require());
    }

    @GetMapping("/{id}/projects")
    public List<ProjectRef> projects(@PathVariable Long id) {
        return userService.projectsFor(id, currentUser.require());
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.update(id, request.name(), request.email(), currentUser.require());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id, currentUser.require());
        return ResponseEntity.noContent().build();
    }
}