package com.linkedin.userservice.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkedin.userservice.config.JwtProperties;
import com.linkedin.userservice.dto.AuthResponse;
import com.linkedin.userservice.dto.LoginRequest;
import com.linkedin.userservice.dto.RegisterRequest;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.entity.UserRole;
import com.linkedin.userservice.event.UserCreatedEvent;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.UserRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jwts.SIG;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String USER_CREATED_TOPIC = "user.created";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JwtProperties jwtProperties;

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

        return buildAuthResponse(savedUser);
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
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.of(
                user,
                generateAccessToken(user.getId(), user.getEmail()),
                generateRefreshToken(user.getId()));
    }

    private String generateAccessToken(String id, String email) {
        return Jwts.builder()
                .claim("userId", id)
                .claim("type", "access")
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.expiration()))
                .issuedAt(new Date())
                .subject(email)
                .signWith(getSigningKey(), SIG.HS256)
                .compact();
    }

    private String generateRefreshToken(String id) {
        return Jwts.builder()
                .claim("userId", id)
                .claim("type", "refresh")
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.refreshExpiration()))
                .issuedAt(new Date())
                .signWith(getSigningKey(), SIG.HS256)
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }
}
