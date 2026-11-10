package com.linkedin.searchservice.event;

/** Mirror of user-service's user.deleted payload. */
public record UserDeletedEvent(String userId) {
}
