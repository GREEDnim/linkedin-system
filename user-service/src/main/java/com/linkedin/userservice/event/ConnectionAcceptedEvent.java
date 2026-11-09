package com.linkedin.userservice.event;

import com.linkedin.userservice.entity.Connection;

/**
 * Published when a connection request is accepted. Consumed by notification-service
 * (notify the requester) and feed-service (start showing each other's posts).
 *
 * <p>Note: there is deliberately no rejected event. LinkedIn never tells the sender
 * that an invitation was ignored, so publishing one would leak it downstream.
 */
public record ConnectionAcceptedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {

    public static ConnectionAcceptedEvent from(Connection connection) {
        return new ConnectionAcceptedEvent(
                connection.getId(),
                connection.getRequesterId(),
                connection.getReceiverId());
    }
}
