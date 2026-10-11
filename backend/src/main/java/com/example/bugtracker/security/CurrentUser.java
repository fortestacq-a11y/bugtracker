package com.example.bugtracker.security;
import com.example.bugtracker.exception.UnauthenticatedException;

import com.example.bugtracker.domain.User;
import com.example.bugtracker.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller from the Bearer token on the current request.
 *
 * <p>The JWT subject is the user id, so this loads the {@link User} row once per
 * request and every service can ask "who is calling?" without threading the
 * principal through every method signature.
 */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * The authenticated user, or empty if the request had no valid token.
     * Never throws — callers decide whether anonymous is acceptable.
     */
    public Optional<User> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        Long userId = extractUserId(auth);
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).filter(User::isEnabled);
    }

    /**
     * The authenticated user, or a 401 if there isn't one. Use in any endpoint
     * the filter chain already requires a token for.
     */
    public User require() {
        return get().orElseThrow(UnauthenticatedException::new);
    }

    private Long extractUserId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return parseSubject(jwtAuth.getToken());
        }
        // Not a JWT principal — anonymous or an error token.
        return null;
    }

    private Long parseSubject(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}