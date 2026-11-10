package com.linkedin.notificationservice.event;

/**
 * Mirror of user-service's connection.requested payload.
 *
 * <p>The recipient is the receiver (the person being asked) and the actor is the
 * requester - the opposite of ConnectionAcceptedEvent, where the requester is the
 * one who wants to know. Field names must match the producer exactly; there are no
 * type headers on the wire.
 */
public record ConnectionRequestedEvent(
        String connectionId,
        String requesterId,
        String receiverId) {
}
