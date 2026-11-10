package com.linkedin.userservice.event;

import com.linkedin.userservice.entity.Connection;

/**
 * Published when a CONNECTED connection is removed by either party.
 * Consumed by feed-service (to stop fanning out to an ex-connection) and
 * notification-service. Emitted with spring.json.add.type.headers: false,
 * so the JSON body is the contract.
 */
public record ConnectionDeletedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {

    public static ConnectionDeletedEvent from(Connection connection) {
        return new ConnectionDeletedEvent(
                connection.getId(),
                connection.getRequesterId(),
                connection.getReceiverId());
    }
}
