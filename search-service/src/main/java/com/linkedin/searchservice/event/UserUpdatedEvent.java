package com.linkedin.searchservice.event;

import java.util.List;

/**
 * Mirror of the event published by user-service on {@code user.updated}: a profile edit.
 *
 * <p>Every field here is searchable, which is the whole point of this service: a user who
 * fills in a headline and a location becomes findable by them. Display assets such as the
 * avatar are deliberately absent - the profile endpoint owns those.
 */
public record UserUpdatedEvent(
        String userId,
        String firstName,
        String lastName,
        String headline,
        String location,
        List<String> skills) {
}
