package com.linkedin.searchservice.event;

/**
 * Mirror of the event published by post-service on {@code post.created}.
 *
 * <p>Field names are the contract (no type headers on the wire). {@code content} is what
 * makes a post findable; {@code imageUrl} is intentionally not part of the event.
 */
public record PostCreatedEvent(
        String postId,
        String authorId,
        String content) {
}
