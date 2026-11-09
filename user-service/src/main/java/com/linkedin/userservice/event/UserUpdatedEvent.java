package com.linkedin.userservice.event;

import java.util.ArrayList;
import java.util.List;

import com.linkedin.userservice.entity.User;

/**
 * Published when a user edits their profile. Consumed by search-service (re-indexing).
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
