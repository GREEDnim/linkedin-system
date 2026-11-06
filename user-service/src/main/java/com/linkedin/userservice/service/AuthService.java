package com.linkedin.userservice.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.linkedin.userservice.UserRole;
import com.linkedin.userservice.dto.AuthResponse;
import com.linkedin.userservice.dto.LoginRequest;
import com.linkedin.userservice.dto.RegisterRequest;
import com.linkedin.userservice.entity.User;
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
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value ("${jwt.secret}")
    private String secretKey;
     @Value ("${jwt.expiration}")
    private long jwtExpiration;
     @Value ("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private static final String USER_CREATED_TOPIC = "user.created";

    public AuthResponse register(RegisterRequest request){
        log.info("Registering user: {}", request.getEmail());

        if(userRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException(
                "Email already registered:"+ request.getEmail()
            );
        }

        User user = User.builder()
        .firstName(request.getFirstName())
        .lastName(request.getLastName())
        .location(request.getLocation())
        .email(request.getEmail())
        .headline(request.getHeadline())
        .password(passwordEncoder.encode(request.getPassword()))
        .role(UserRole.NORMAL_USER)
        .build();

        User savedUser = userRepository.save(user);
        log.info("User registered: {}", savedUser.getId());

        // Publish user created to search service.
        Map<String, Object> userCreatedEvent = new HashMap<>();
        userCreatedEvent.put("userId", savedUser.getId());
        userCreatedEvent.put("firstName", savedUser.getFirstName());
        userCreatedEvent.put("lastName", savedUser.getLastName());
        userCreatedEvent.put("email", savedUser.getEmail());

        kafkaTemplate.send(USER_CREATED_TOPIC, savedUser.getId(), userCreatedEvent);

        log.info("user.created event published: {}", savedUser.getId());

        return buildAuthResponse(savedUser);

    }

    public AuthResponse login(LoginRequest request){
        log.info("Login attempt: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new RuntimeException("User not found:"+ request.getEmail()));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new RuntimeException("Invalid Credentials");
        }

        log.info("login successful: {}", user.getId());
        return buildAuthResponse(user);


    }
    private AuthResponse buildAuthResponse(User user){
        AuthResponse res = new AuthResponse();
        res.setAccessToken(generateAccessToken(user.getId(), user.getEmail()));
        res.setRefreshToken(generateRefreshToken(user.getId()));
        res.setUserId(user.getId());
        res.setEmail(user.getEmail());
        res.setFirstName(user.getFirstName());
        res.setLastName(user.getLastName());
        return res;
    }

    private String generateAccessToken(String id, String email){
        return Jwts.builder()
        .claim("userId", id)
        .claim("type", "access")
        .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
        .issuedAt(new Date())
        .subject(email)
        .signWith(getSignInKey(), SIG.HS256)
        .compact();
    }

    private String generateRefreshToken(String id){
        return Jwts.builder()
        .claim("userId", id)
        .claim("type", "refresh")
        .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
        .issuedAt(new Date())
        .signWith(getSignInKey(), SIG.HS256)
        .compact();
    }

    private SecretKey getSignInKey(){
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
