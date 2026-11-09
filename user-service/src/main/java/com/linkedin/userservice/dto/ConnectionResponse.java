package com.linkedin.userservice.dto;

import java.time.LocalDateTime;

import com.linkedin.userservice.entity.Connection;
import com.linkedin.userservice.entity.ConnectionStatus;

public record ConnectionResponse(
        String id,
        String requesterId,
        String receiverId,
        ConnectionStatus status,
        LocalDateTime createdAt) {

    public static ConnectionResponse from(Connection connection) {
        return new ConnectionResponse(
                connection.getId(),
                connection.getRequesterId(),
                connection.getReceiverId(),
                connection.getStatus(),
                connection.getCreatedAt());
    }
}
