package com.linkedin.postservice.event;

/**
 * Published when a like is removed, so notification-service can withdraw the
 * "X liked your post" notification instead of leaving a stale one behind.
 */
public record PostUnlikedEvent(
        String postId,
        String authorId) {
}
