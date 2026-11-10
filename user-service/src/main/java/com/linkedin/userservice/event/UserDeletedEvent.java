package com.linkedin.userservice.event;

/**
 * Published when an account is deleted.
 *
 * <p>Carries only the id, on purpose. Every consumer needs the same thing - which account to
 * forget - and anything else in the payload would be stale personal data sitting in a topic
 * that is retained for days after the deletion.
 */
public record UserDeletedEvent(String userId) {
}
