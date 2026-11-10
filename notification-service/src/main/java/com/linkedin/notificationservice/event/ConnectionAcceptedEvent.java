package com.linkedin.notificationservice.event;

/**
 * Mirror of user-service's connection.accepted payload.
 *
 * <p>Here the requester is the recipient: they sent the invitation and are waiting
 * to hear back. The receiver is the actor because they performed the acceptance.
 */
public record ConnectionAcceptedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {
}
