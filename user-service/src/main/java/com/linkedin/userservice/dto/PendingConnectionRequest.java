package com.linkedin.userservice.dto;

import java.time.LocalDateTime;

import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.User;

/**
 * One incoming (unanswered) connection request, shown to the receiver.
 * Carries just enough of the requester's profile to render a notification
 * without a second round trip — deliberately no email.
 */
public record PendingConnectionRequest(
        String connectionId,
        String requesterId,
        String requesterFirstName,
        String requesterLastName,
        String requesterHeadline,
        String requesterProfilePhotoUrl,
        LocalDateTime requestedAt) {

    public static PendingConnectionRequest from(Connection connection, User requester) {
        return new PendingConnectionRequest(
                connection.getId(),
                connection.getRequesterId(),
                requester == null ? null : requester.getFirstName(),
                requester == null ? null : requester.getLastName(),
                requester == null ? null : requester.getHeadline(),
                requester == null ? null : requester.getProfilePhotoUrl(),
                connection.getCreatedAt());
    }
}
