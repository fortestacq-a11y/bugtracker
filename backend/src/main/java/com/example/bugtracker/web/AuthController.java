package com.example.bugtracker.web;

import com.example.bugtracker.security.CurrentUser;
import com.example.bugtracker.service.AuthService;
import com.example.bugtracker.web.dto.AuthDtos.AuthResponse;
import com.example.bugtracker.web.dto.AuthDtos.LoginRequest;
import com.example.bugtracker.web.dto.AuthDtos.RegisterRequest;
import com.example.bugtracker.web.dto.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/auth — the only endpoints reachable without a token are register and login.
 *
 * <p>Thin by design: no authorization logic lives in a controller (plan §10.4).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Requires a valid Bearer token; a missing or expired one yields 401. */
    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }
}