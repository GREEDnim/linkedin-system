package com.linkedin.searchservice.event;

/**
 * Mirror of the event published by post-service on {@code post.deleted}.
 * Without this listener a deleted post would keep answering searches forever.
 */
public record PostDeletedEvent(
        String postId,
        String authorId) {
}
