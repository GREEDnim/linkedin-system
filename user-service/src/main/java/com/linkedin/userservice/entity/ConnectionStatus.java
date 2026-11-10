package com.linkedin.userservice.entity;

public enum ConnectionStatus {
    PENDING,
    CONNECTED,
    REJECTED,
    /** Was CONNECTED, then one side removed the connection. Re-connectable. */
    REMOVED
}
