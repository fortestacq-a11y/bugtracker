package com.example.bugtracker.service;

import com.example.bugtracker.exception.EmailAlreadyRegisteredException;
import com.example.bugtracker.exception.UserStillInProjectsException;

import com.example.bugtracker.domain.ProjectMember;
import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.ProjectMemberRepository;
import com.example.bugtracker.repository.UserRepository;
import com.example.bugtracker.exception.NotFoundException;
import com.example.bugtracker.web.dto.AuthDtos.UserResponse;
import com.example.bugtracker.web.dto.IssueDtos.ProjectRef;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User endpoints, scoped tightly on purpose.
 *
 * <p>There is no {@code GET /api/users}. A global user list would defeat the whole
 * project-membership model: names and emails of every account would be readable by
 * anyone holding a valid token. The assignee dropdown instead calls
 * {@code GET /api/projects/{id}/members}, which only ever returns people in a
 * project the caller already belongs to.
 *
 * <p>Consequence: {@code GET/DELETE /api/users/{id}} returns 404 for anyone but the
 * account owner (or an admin), so a caller cannot even confirm that an id exists.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository memberRepository;

    public UserService(UserRepository userRepository, ProjectMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    /** The caller's own profile. */
    @Transactional(readOnly = true)
    public UserResponse me(User user) {
        return UserResponse.from(user);
    }

    /** A single user, visible only to themselves or an admin. */
    @Transactional(readOnly = true)
    public UserResponse getById(Long id, User caller) {
        return UserResponse.from(requireSelfOrAdmin(id, caller));
    }

    /** Which projects this user belongs to. Self or admin only. */
    @Transactional(readOnly = true)
    public List<ProjectRef> projectsFor(Long id, User caller) {
        User target = requireSelfOrAdmin(id, caller);

        return memberRepository.findByUserId(target.getId()).stream()
                .map(ProjectMember::getProject)
                .map(p -> new ProjectRef(p.getId(), p.getProjectKey(), p.getName()))
                .toList();
    }

    /**
     * Self-service profile edit. Admins may edit anyone's name and email.
     *
     * <p>{@code UserRole} is deliberately not editable here — otherwise an admin
     * flag could be granted (or revoked) by accident through a profile form.
     */
    @Transactional
    public UserResponse update(Long id, String name, String email, User caller) {
        User target = requireSelfOrAdmin(id, caller);

        if (name != null && !name.isBlank()) {
            target.setName(name.trim());
        }
        if (email != null && !email.isBlank()) {
            String normalised = email.trim().toLowerCase();
            boolean taken = userRepository.findByEmailIgnoreCase(normalised)
                    .filter(other -> !other.getId().equals(target.getId()))
                    .isPresent();
            if (taken) {
                throw new EmailAlreadyRegisteredException();
            }
            target.setEmail(normalised);
        }
        return UserResponse.from(userRepository.save(target));
    }

    /**
     * Account removal. Refused while the user still belongs to projects — that
     * would silently strip every one of them of a member. Non-owners get 404.
     */
    @Transactional
    public void delete(Long id, User caller) {
        User target = requireSelfOrAdmin(id, caller);

        List<ProjectMember> memberships = memberRepository.findByUserId(target.getId());
        if (!memberships.isEmpty()) {
            throw new UserStillInProjectsException(memberships.size());
        }
        userRepository.delete(target);
    }

    /**
     * 404 rather than 403 when the caller is neither the account owner nor an
     * admin: a stranger should not learn that this id corresponds to a real user.
     */
    private User requireSelfOrAdmin(Long id, User caller) {
        boolean permitted = id.equals(caller.getId()) || caller.isAdmin();
        if (!permitted) {
            throw new NotFoundException("User not found");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}