package com.example.bugtracker.service;

import com.example.bugtracker.exception.AccountDisabledException;
import com.example.bugtracker.exception.BadCredentialsException;
import com.example.bugtracker.exception.EmailAlreadyRegisteredException;

import com.example.bugtracker.domain.User;
import com.example.bugtracker.domain.UserRole;
import com.example.bugtracker.repository.UserRepository;
import com.example.bugtracker.security.CurrentUser;
import com.example.bugtracker.security.JwtService;
import com.example.bugtracker.web.dto.AuthDtos.AuthResponse;
import com.example.bugtracker.web.dto.AuthDtos.LoginRequest;
import com.example.bugtracker.web.dto.AuthDtos.RegisterRequest;
import com.example.bugtracker.web.dto.AuthDtos.UserResponse;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login and "who am I".
 *
 * <p>Failure messages are deliberately vague: an unknown email and a wrong
 * password produce the same error, so the endpoint can't be used to enumerate
 * which addresses have accounts.
 */
@Service
public class AuthService {

    /** Fixed palette so a user's avatar colour is stable, derived from their id. */
    private static final List<String> AVATAR_COLORS = List.of(
            "#6366f1", "#ec4899", "#14b8a6", "#f59e0b", "#8b5cf6", "#ef4444", "#06b6d4", "#84cc16");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUser = currentUser;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()));
        // Everyone who registers through the public endpoint is a plain MEMBER.
        // ADMIN is granted deliberately, never from a request field — otherwise any
        // caller could make themselves an admin by adding a role to the JSON body.
        user.setRole(UserRole.MEMBER);
        user.setEnabled(true);

        User saved = userRepository.saveAndFlush(user);
        saved.setAvatarColor(colourFor(saved.getId()));
        userRepository.save(saved);

        return authResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(BadCredentialsException::new);

        // Same message whether the account is missing or the password is wrong —
        // otherwise "no such user" is a free account-enumeration oracle.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException();
        }
        if (!user.isEnabled()) {
            throw new AccountDisabledException();
        }
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUser.require());
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(
                jwtService.issueToken(user.getId(), user.getEmail()),
                "Bearer",
                jwtService.ttlSeconds(),
                UserResponse.from(user));
    }

    private String colourFor(Long id) {
        return AVATAR_COLORS.get((int) (Math.abs(id) % AVATAR_COLORS.size()));
    }
}