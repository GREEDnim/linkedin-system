package com.linkedin.userservice.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.linkedin.userservice.dto.ConnectionResponse;
import com.linkedin.userservice.dto.PendingConnectionRequest;
import com.linkedin.userservice.dto.UpdateProfileRequest;
import com.linkedin.userservice.dto.UserResponse;
import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.ConnectionStatus;
import com.linkedin.userservice.entity.User;
import com.linkedin.userservice.event.ConnectionAcceptedEvent;
import com.linkedin.userservice.event.ConnectionDeletedEvent;
import com.linkedin.userservice.event.ConnectionRequestedEvent;
import com.linkedin.userservice.event.UserDeletedEvent;
import com.linkedin.userservice.event.UserUpdatedEvent;
import com.linkedin.userservice.exception.ApiException;
import com.linkedin.userservice.repository.ConnectionRepository;
import com.linkedin.userservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private static final String CONNECTION_REQUESTED_TOPIC = "connection.requested";
    private static final String CONNECTION_ACCEPTED_TOPIC = "connection.accepted";
    private static final String CONNECTION_DELETED_TOPIC = "connection.deleted";
    private static final String USER_UPDATED_TOPIC = "user.updated";
    private static final String USER_DELETED_TOPIC = "user.deleted";

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ------------------------------------------------------------------ connections

    /**
     * Creates a PENDING connection request. Mirrors LinkedIn's invitation rules:
     * CONNECTED and PENDING block a new request, while REJECTED does not — an
     * ignored invitation is silent and never permanent.
     */
    @Transactional
    public ConnectionResponse sendConnectionRequest(String requesterId, String receiverId) {

        if (requesterId.equals(receiverId)) {
            throw ApiException.badRequest("You cannot send a connection request to yourself");
        }
        if (!userRepository.existsById(receiverId)) {
            throw ApiException.notFound("User not found: " + receiverId);
        }

        // One row per pair, so an earlier REJECTED row has to be reused.
        Connection connection = connectionRepository.findBetween(requesterId, receiverId).orElse(null);

        if (connection == null) {
            connection = new Connection();
        } else {
            switch (connection.getStatus()) {
                case CONNECTED -> throw ApiException.conflict(
                        "You are already connected with this user");
                case PENDING -> throw ApiException.conflict(
                        "A connection request already exists between these users");
                case REJECTED, REMOVED -> log.info(
                        "Re-request allowed after {}: {} -> {}",
                        connection.getStatus(), requesterId, receiverId);
            }
        }

        connection.setRequesterId(requesterId);
        connection.setReceiverId(receiverId);
        connection.setStatus(ConnectionStatus.PENDING);

        Connection saved = connectionRepository.saveAndFlush(connection);

        kafkaTemplate.send(CONNECTION_REQUESTED_TOPIC, saved.getId(), ConnectionRequestedEvent.from(saved));
        log.info("Connection request sent: {} -> {}", requesterId, receiverId);

        return ConnectionResponse.from(saved);
    }

    /** Accepts a PENDING request. Only the receiver may accept it. */
    @Transactional
    public ConnectionResponse acceptConnectionRequest(String connectionId, String requestingUserId) {

        Connection connection = loadPendingForReceiver(connectionId, requestingUserId);

        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConnectedAt(LocalDateTime.now());
        Connection saved = connectionRepository.saveAndFlush(connection);

        kafkaTemplate.send(CONNECTION_ACCEPTED_TOPIC, saved.getId(), ConnectionAcceptedEvent.from(saved));
        log.info("Connection accepted: {}", connectionId);

        return ConnectionResponse.from(saved);
    }

    /**
     * Declines a PENDING request. Mirrors LinkedIn's "Ignore": the sender is never
     * told, and the row survives as REJECTED so either side may try again later.
     */
    @Transactional
    public ConnectionResponse rejectConnectionRequest(String connectionId, String requestingUserId) {

        Connection connection = loadPendingForReceiver(connectionId, requestingUserId);

        connection.setStatus(ConnectionStatus.REJECTED);
        Connection saved = connectionRepository.saveAndFlush(connection);

        // No event on purpose: announcing a rejection would break the silence
        // LinkedIn guarantees, and notification-service would have nothing to send.
        log.info("Connection rejected: {}", connectionId);

        return ConnectionResponse.from(saved);
    }

    /**
     * Removes an active connection. Either party may do it, exactly like LinkedIn
     * (no "only the person who asked can undo it" rule).
     *
     * <p>The row survives as REMOVED rather than being deleted: it keeps the audit
     * trail, and — because one row per pair is enforced by the unique key — a later
     * re-connect reuses it (see {@link #sendConnectionRequest}). This method is the
     * mirror of accept: it flips CONNECTED -> REMOVED and announces
     * connection.deleted so downstream projections (feed-service's graph) can
     * stop treating the pair as connected.
     */
    @Transactional
    public ConnectionResponse disconnect(String connectionId, String requestingUserId) {

        Connection connection = connectionRepository.findById(connectionId).orElseThrow(
                () -> ApiException.notFound("Connection not found: " + connectionId));

        boolean participant = connection.getRequesterId().equals(requestingUserId)
                || connection.getReceiverId().equals(requestingUserId);
        if (!participant) {
            throw ApiException.forbidden("You are not part of this connection");
        }
        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            throw ApiException.conflict("Connection is not active: " + connection.getStatus());
        }

        connection.setStatus(ConnectionStatus.REMOVED);
        Connection saved = connectionRepository.saveAndFlush(connection);

        kafkaTemplate.send(CONNECTION_DELETED_TOPIC, saved.getId(), ConnectionDeletedEvent.from(saved));
        log.info("Connection removed: {} by {}", connectionId, requestingUserId);

        return ConnectionResponse.from(saved);
    }

    /**
     * Shared authorization for accept/reject: the row must exist, the caller must be
     * its receiver, and the request must still be PENDING.
     */
    private Connection loadPendingForReceiver(String connectionId, String requestingUserId) {

        Connection connection = connectionRepository.findById(connectionId).orElseThrow(
                () -> ApiException.notFound("Connection not found: " + connectionId));

        if (!connection.getReceiverId().equals(requestingUserId)) {
            throw ApiException.forbidden("Only the receiver can respond to this connection request");
        }
        if (connection.getStatus() != ConnectionStatus.PENDING) {
            throw ApiException.conflict("Connection is already " + connection.getStatus());
        }
        return connection;
    }

    /** All CONNECTED users, in either direction. Two queries total (no N+1). */
    @Transactional(readOnly = true)
    public List<UserResponse> getConnections(String userId) {

        List<Connection> connections =
                connectionRepository.findByUserIdAndStatus(userId, ConnectionStatus.CONNECTED);

        Set<String> otherUserIds = connections.stream()
                .map(c -> c.getRequesterId().equals(userId) ? c.getReceiverId() : c.getRequesterId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, User> usersById = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return otherUserIds.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(UserResponse::from)
                .toList();
    }

    /** Incoming, unanswered requests, newest first. Two queries total (no N+1). */
    @Transactional(readOnly = true)
    public List<PendingConnectionRequest> getPendingConnectionRequests(String userId) {

        List<Connection> pendingRequests = connectionRepository
                .findByReceiverIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING);

        if (pendingRequests.isEmpty()) {
            return List.of();
        }

        Set<String> requesterIds = pendingRequests.stream()
                .map(Connection::getRequesterId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, User> requestersById = userRepository.findAllById(requesterIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return pendingRequests.stream()
                .map(connection -> PendingConnectionRequest.from(
                        connection, requestersById.get(connection.getRequesterId())))
                .toList();
    }

    // --------------------------------------------------------------------- profiles

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String userId) {
        return UserResponse.from(findUserOrThrow(userId));
    }

    /** Partial update: only non-null fields are applied. */
    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {

        User user = findUserOrThrow(userId);

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.headline() != null) user.setHeadline(request.headline());
        if (request.about() != null) user.setAbout(request.about());
        if (request.location() != null) user.setLocation(request.location());
        if (request.profilePhotoUrl() != null) user.setProfilePhotoUrl(request.profilePhotoUrl());
        if (request.coverPhotoUrl() != null) user.setCoverPhotoUrl(request.coverPhotoUrl());
        if (request.skills() != null) user.setSkills(request.skills());

        User saved = userRepository.save(user);

        kafkaTemplate.send(USER_UPDATED_TOPIC, saved.getId(), UserUpdatedEvent.from(saved));
        log.info("user updated successfully and user.updated published: {}", saved.getId());

        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse uploadProfilePhoto(String userId, MultipartFile file) {

        User user = findUserOrThrow(userId);

        // Remember the outgoing key before overwriting it: without it the old avatar becomes an
        // object nothing points at, which is the same orphan problem in miniature.
        String previousKey = user.getProfilePhotoKey();

        S3Service.StoredObject uploaded = s3Service.uploadFile(file, "profiles/" + userId + "/avatar");
        user.setProfilePhotoUrl(uploaded.url());
        user.setProfilePhotoKey(uploaded.key());
        User saved = userRepository.save(user);

        s3Service.deleteObject(previousKey);

        log.info("profile photo uploaded for user: {}", userId);
        return UserResponse.from(saved);
    }

    /**
     * Deletes an account and everything that belongs to it here.
     *
     * <p>Order matters. Connections go first because they are mutual and reference this user
     * from either side; skills are removed with their owner, since they are an
     * {@code @ElementCollection} of the user rather than rows of their own. The avatar is then
     * deleted from object storage, and the event is published last: consumers must not learn
     * about the deletion before it is true.
     *
     * <p>Other services clean up after themselves from the event. post-service removes the
     * user's posts (which un-fans them through the existing post.deleted path), search-service
     * drops the index document, feed-service drops the feed and the graph entry.
     *
     * <p>Access tokens are not revoked: they are stateless and live at most fifteen minutes.
     * A refresh token stops working immediately, because the refresh path loads the user.
     */
    @Transactional
    public void deleteAccount(String userId) {

        User user = findUserOrThrow(userId);
        String avatarKey = user.getProfilePhotoKey();

        int connections = connectionRepository.deleteAllForUser(userId);
        userRepository.delete(user);

        s3Service.deleteObject(avatarKey);

        kafkaTemplate.send(USER_DELETED_TOPIC, userId, new UserDeletedEvent(userId));
        log.info("Account {} deleted ({} connections removed)", userId, connections);
    }

    private User findUserOrThrow(String userId) {
        return userRepository.findById(userId).orElseThrow(
                () -> ApiException.notFound("User not found: " + userId));
    }
}
