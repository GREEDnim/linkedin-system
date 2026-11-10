package com.linkedin.searchservice.event;

/**
 * Mirror of the event published by user-service on {@code user.created}.
 *
 * <p>The field names are the contract: events are published without type headers, so
 * this record has to match the producer's JSON exactly. Registration genuinely has no
 * headline, location or skills yet, which is why those only arrive with
 * {@code user.updated} - and why the create handler writes a partial document.
 */
public record UserCreatedEvent(
        String userId,
        String firstName,
        String lastName,
        String email) {
}
