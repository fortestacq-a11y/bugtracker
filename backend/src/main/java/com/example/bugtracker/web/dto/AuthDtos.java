package com.example.bugtracker.web.dto;

import com.example.bugtracker.domain.User;
import com.example.bugtracker.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * JSON shapes for /api/auth/**.
 *
 * <p>Note what is absent: no response type anywhere carries {@code passwordHash}.
 * The field exists on {@link User} but is never copied into a DTO, so it cannot
 * leak through this API by accident.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserResponse(
            Long id,
            String name,
            String email,
            UserRole role,
            String avatarColor,
            boolean enabled,
            Instant createdAt) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getRole(),
                    user.getAvatarColor(),
                    user.isEnabled(),
                    user.getCreatedAt());
        }
    }

    /**
     * Returned by register, login and me. {@code tokenType} is "Bearer" so the
     * client can build the header without hardcoding it.
     */
    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserResponse user) {
    }
}