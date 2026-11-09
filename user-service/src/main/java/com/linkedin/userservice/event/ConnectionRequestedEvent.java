package com.linkedin.userservice.event;

import com.linkedin.userservice.entity.Connection;

/**
 * Published when a connection request is created. Consumed by notification-service.
 */
public record ConnectionRequestedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {

    public static ConnectionRequestedEvent from(Connection connection) {
        return new ConnectionRequestedEvent(
                connection.getId(),
                connection.getRequesterId(),
                connection.getReceiverId());
    }
}
