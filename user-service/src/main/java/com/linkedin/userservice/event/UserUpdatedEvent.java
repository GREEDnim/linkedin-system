package com.linkedin.userservice.event;

import java.util.ArrayList;
import java.util.List;

import com.linkedin.userservice.entity.User;

/**
 * Published when a user edits their profile. Consumed by search-service (re-indexing).
 *
 * <p>The field names are the contract: events carry no type headers, so this record and
 * the consumer's mirror must stay in step.
 *
 * <p>Deliberately carries searchable profile text only. Display assets such as
 * {@code profilePhotoUrl} are not indexed, so they do not belong here - a search result
 * identifies who to look at, and the client renders the card from the profile endpoint.
 */
public record UserUpdatedEvent(
        String userId,
        String firstName,
        String lastName,
        String headline,
        String location,
        List<String> skills) {

    public static UserUpdatedEvent from(User user) {
        return new UserUpdatedEvent(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getHeadline(),
                user.getLocation(),
                user.getSkills() == null ? List.of() : new ArrayList<>(user.getSkills()));
    }
}
