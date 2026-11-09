package com.linkedin.userservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.linkedin.userservice.dto.ConnectionResponse;
import com.linkedin.userservice.dto.PendingConnectionRequest;
import com.linkedin.userservice.dto.UpdateProfileRequest;
import com.linkedin.userservice.dto.UserResponse;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Profile and connection endpoints.
 *
 * <p>The caller is identified by the X-User-Id header, which the api-gateway is
 * expected to set after validating the JWT. Endpoints that mutate a user's own
 * data therefore call requireSelf(...) — the header alone is not trustworthy.
 */
@RestController
@RequestMapping("/api/v1/users")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ------------------------------------------------------------------- profiles

    /** Anyone authenticated may look at any profile. */
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        log.info("Get profile: {}, requested by: {}", userId, requestingUserId);
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @Valid @RequestBody UpdateProfileRequest request) {

        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PostMapping("/{userId}/upload/profile-photo")
    public ResponseEntity<UserResponse> uploadProfilePhoto(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId,
            @RequestParam("file") MultipartFile file) {

        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.uploadProfilePhoto(userId, file));
    }

    // ---------------------------------------------------------------- connections

    /** Creates a connection request: requester = caller, receiver = path variable. */
    @PostMapping("/{targetUserId}/connect")
    public ResponseEntity<ConnectionResponse> sendConnectionRequest(
            @PathVariable String targetUserId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.sendConnectionRequest(requestingUserId, targetUserId));
    }

    @PostMapping("/connections/{connectionId}/accept")
    public ResponseEntity<ConnectionResponse> acceptConnection(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        return ResponseEntity.ok(userService.acceptConnectionRequest(connectionId, requestingUserId));
    }

    @PostMapping("/connections/{connectionId}/reject")
    public ResponseEntity<ConnectionResponse> rejectConnection(
            @PathVariable String connectionId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        return ResponseEntity.ok(userService.rejectConnectionRequest(connectionId, requestingUserId));
    }

    @GetMapping("/{userId}/connections")
    public ResponseEntity<List<UserResponse>> getConnections(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.getConnections(userId));
    }

    /** Incoming requests waiting for this user to accept or reject. */
    @GetMapping("/{userId}/connections/pending")
    public ResponseEntity<List<PendingConnectionRequest>> getPendingConnectionRequests(
            @PathVariable String userId,
            @RequestHeader("X-User-Id") String requestingUserId) {

        requireSelf(userId, requestingUserId);
        return ResponseEntity.ok(userService.getPendingConnectionRequests(userId));
    }

    /** A user may only act on their own profile data. */
    private void requireSelf(String userId, String requestingUserId) {
        if (!userId.equals(requestingUserId)) {
            throw ApiException.forbidden("You may only act on your own profile");
        }
    }
}
