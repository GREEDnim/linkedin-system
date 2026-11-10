package com.linkedin.userservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkedin.userservice.dto.AuthResponse;
import com.linkedin.userservice.dto.LoginRequest;
import com.linkedin.userservice.dto.LogoutRequest;
import com.linkedin.userservice.dto.RefreshRequest;
import com.linkedin.userservice.dto.RegisterRequest;
import com.linkedin.userservice.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The only endpoints reachable without an access token.
 *
 * <p>refresh and logout are public by necessity, not by oversight: the caller of refresh has
 * an expired access token by definition, and logout must still work when the short-lived
 * token is gone. Both authenticate with the refresh token they carry.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request: {}", request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request: {}", request.email());
        return ResponseEntity.ok(authService.login(request));
    }

    /** Rotates a refresh token: the response contains a new pair and the old one is spent. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Refresh request");
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    /** Ends the session. Idempotent and quiet - see AuthService.logout. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
