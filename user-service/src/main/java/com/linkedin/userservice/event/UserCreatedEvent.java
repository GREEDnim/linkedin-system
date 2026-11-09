package com.linkedin.userservice.event;

import com.linkedin.userservice.entity.User;

/**
 * Published when a new user registers. Consumed by search-service (indexing).
 * Emitted with spring.json.add.type.headers: false, so the JSON body is the contract.
 */
public record UserCreatedEvent(
        String userId,
        String firstName,
        String lastName,
        String email) {

    public static UserCreatedEvent from(User user) {
        return new UserCreatedEvent(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail());
    }
}
