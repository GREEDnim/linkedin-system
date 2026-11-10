package com.linkedin.notificationservice.event;

/**
 * Mirror of user-service's user.created payload.
 *
 * <p>Consumed for the welcome notice only. Registration is the one event where
 * recipient and subject are the same user and there is no actor.
 */
public record UserCreatedEvent(
        String userId,
        String firstName,
        String lastName,
        String email) {
}
