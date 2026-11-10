package com.linkedin.userservice.service;

import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkedin.userservice.dto.AuthResponse;
import com.linkedin.userservice.dto.LoginRequest;
import com.linkedin.userservice.dto.RegisterRequest;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.entity.UserRole;
import com.linkedin.userservice.event.UserCreatedEvent;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.UserRepository;
import com.linkedin.userservice.security.JwtService;
import com.linkedin.userservice.security.TokenStore;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registration, login, and the refresh lifecycle.
 *
 * <p>The token model is the standard one: a short-lived access token that is never stored
 * (every service can verify it offline), and a long-lived refresh token that is stored so
 * it can be rotated and revoked. Each login - not each token - has a session id, so
 * logging out or detecting a replay ends the whole login in one operation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String USER_CREATED_TOPIC = "user.created";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JwtService jwtService;
    private final TokenStore tokenStore;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering user: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email already registered: " + request.email());
        }

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .location(request.location())
                .email(request.email())
                .headline(request.headline())
                .password(passwordEncoder.encode(request.password()))
                .role(UserRole.NORMAL_USER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered: {}", savedUser.getId());

        // Publish to search-service for indexing.
        kafkaTemplate.send(USER_CREATED_TOPIC, savedUser.getId(), UserCreatedEvent.from(savedUser));
        log.info("user.created event published: {}", savedUser.getId());

        return startSession(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt: {}", request.email());

        // Same message for "unknown email" and "wrong password" so the endpoint
        // cannot be used to enumerate which emails are registered.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw ApiException.unauthorized("Invalid email or password");
        }

        log.info("Login successful: {}", user.getId());
        return startSession(user);
    }

    /**
     * Exchanges a refresh token for a new pair - and consumes the old one.
     *
     * <p>Rotation is what makes theft detectable: a token that verifies but was already
     * spent can only come from a second party holding a copy, so the session is revoked
     * rather than a new pair being issued. Presenting a token twice is therefore not a
     * harmless retry, which is why clients must persist the new pair from the response.
     *
     * <p>Access tokens already issued stay valid until they expire (minutes); this is the
     * deliberate trade that keeps per-request authorisation stateless.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {

        Claims claims = parseRefreshToken(refreshToken);

        String sessionId = claims.get(JwtService.CLAIM_SESSION, String.class);
        String jti = claims.getId();
        String userId = claims.getSubject();

        if (!tokenStore.isSessionActive(sessionId)) {
            throw ApiException.unauthorized("Session has ended, please log in again");
        }

        // GETDEL: only one caller can win. A null here means this token was already spent
        // - the signature is still valid, so it is not tampering, it is a replayed copy.
        if (tokenStore.consumeRefreshToken(jti) == null) {
            tokenStore.endSession(sessionId);
            log.warn("Refresh token reuse detected for session {} - session revoked", sessionId);
            throw ApiException.unauthorized("Refresh token already used; session revoked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));

        log.info("Refreshed session {} for user {}", sessionId, userId);
        return issueForSession(user, sessionId);
    }

    /**
     * Ends the session behind a refresh token.
     *
     * <p>Deliberately quiet: an expired, malformed or already-revoked token still produces a
     * success, so logout cannot be used to probe whether a token exists.
     */
    public void logout(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtService.parse(refreshToken);
            String sessionId = claims.get(JwtService.CLAIM_SESSION, String.class);
            if (sessionId != null) {
                tokenStore.endSession(sessionId);
                log.info("Session {} ended by logout", sessionId);
            }
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Ignoring unusable token at logout: {}", ex.getMessage());
        }
    }

    private Claims parseRefreshToken(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token is required");
        }

        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }

        // The mirror image of the gateway's check: it refuses refresh tokens, this refuses
        // access tokens - so neither kind can be used where the other belongs.
        if (!JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw ApiException.unauthorized("Token is not a refresh token");
        }
        if (claims.getId() == null || claims.getSubject() == null
                || claims.get(JwtService.CLAIM_SESSION, String.class) == null) {
            throw ApiException.unauthorized("Refresh token is missing required claims");
        }

        return claims;
    }

    /** One login = one session id, which owns every token issued for it. */
    private AuthResponse startSession(User user) {
        String sessionId = UUID.randomUUID().toString();
        tokenStore.startSession(sessionId);
        return issueForSession(user, sessionId);
    }

    private AuthResponse issueForSession(User user, String sessionId) {
        JwtService.TokenPair pair = jwtService.issue(user, sessionId);
        // Registered before it is handed out: a token the store has never seen cannot be used.
        tokenStore.storeRefreshToken(pair.refreshJti(), sessionId);
        return AuthResponse.of(user, pair.accessToken(), pair.refreshToken(), pair.accessTtlSeconds());
    }
}
